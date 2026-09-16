package com.port.inspection.service;

import com.port.inspection.dto.Dtos;
import com.port.inspection.exception.BizException;
import com.port.inspection.model.*;
import com.port.inspection.model.enums.*;
import com.port.inspection.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 申报单：商家、仓库、报关员、客服、海关接口、财务在同一申报单中协同处理。
 */
@Service
@RequiredArgsConstructor
public class DeclarationService {

    private final DeclarationRepository declarationRepository;
    private final DeclarationParticipantRepository participantRepository;
    private final ParcelRepository parcelRepository;
    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final TaxRecordRepository taxRecordRepository;
    private final CustomsTaskRepository customsTaskRepository;
    private final PrecheckService precheckService;
    private final ParcelEventService eventService;

    @Value("${app.customs-delay-seconds:3}")
    private long customsDelaySeconds;

    // ---------------- 创建申报单 ----------------

    @Transactional
    public Declaration createDeclaration(Long parcelId, User actor) {
        Parcel p = parcelRepository.findById(parcelId).orElseThrow(() -> BizException.notFound("包裹"));
        if (actor.getRole() == Role.MERCHANT && !Objects.equals(p.getMerchantId(), actor.getMerchantId())) {
            throw BizException.forbidden("只能为本商家包裹创建申报单");
        }
        if (p.getStatus() != PackageStatus.PRECHECK_PASSED) {
            throw new BizException("包裹须先通过申报前检查，当前状态: " + p.getStatus());
        }
        Merchant m = merchantRepository.findById(p.getMerchantId()).orElseThrow(() -> BizException.notFound("商家"));

        // 高风险商家：限制批量申报
        if (p.getBatchNo() != null && !p.getBatchNo().isBlank()) {
            long batchCount = parcelRepository.findByBatchNo(p.getBatchNo()).size();
            if (batchCount > m.getBatchLimit()) {
                throw new BizException("高风险商家批量申报受限：批次 " + p.getBatchNo()
                        + " 含 " + batchCount + " 单，超出限额 " + m.getBatchLimit());
            }
        }
        // 高风险商家：要求提前上传完整票据
        if (Boolean.TRUE.equals(m.getRequireAdvanceDocs())
                && materialRepository.findByParcelIdAndMaterialType(parcelId, MaterialType.INVOICE).isEmpty()) {
            throw new BizException("高风险商家须提前上传完整票据（发票）后才能申报");
        }

        Declaration d = new Declaration();
        d.setDeclarationNo(genNo("DEC"));
        d.setParcelId(parcelId);
        d.setMerchantId(m.getId());
        d.setTaxAmount(precheckService.computeTax(p));
        declarationRepository.save(d);

        // 同一申报单协同方：商家、仓库、报关员、客服、海关接口、财务
        addParticipant(d, actor.getRole() == Role.MERCHANT ? actor : firstUser(Role.MERCHANT, m.getId()), Role.MERCHANT);
        addParticipant(d, firstUser(Role.WAREHOUSE, null), Role.WAREHOUSE);
        addParticipant(d, firstUser(Role.BROKER, null), Role.BROKER);
        addParticipant(d, firstUser(Role.CS, null), Role.CS);
        addParticipant(d, firstUser(Role.CUSTOMS, null), Role.CUSTOMS);
        addParticipant(d, firstUser(Role.FINANCE, null), Role.FINANCE);

        // 生成税费记录（财务待缴）
        TaxRecord tax = new TaxRecord();
        tax.setDeclarationId(d.getId());
        tax.setParcelId(parcelId);
        tax.setTaxType(p.getTradeMode() == TradeMode.BONDED ? "跨境电商综合税" : "一般贸易税");
        tax.setAmount(d.getTaxAmount());
        taxRecordRepository.save(tax);

        m.setTotalDeclarations(m.getTotalDeclarations() + 1);
        merchantRepository.save(m);

        eventService.record(parcelId, p.getStatus(), p.getStatus(), "申报单创建", actor,
                "申报单 " + d.getDeclarationNo() + " 已创建，预估税费 ¥" + d.getTaxAmount()
                        + "，协同方：商家/仓库/报关员/客服/海关接口/财务");
        return d;
    }

    private void addParticipant(Declaration d, User user, Role role) {
        if (user == null) return;
        DeclarationParticipant dp = new DeclarationParticipant();
        dp.setDeclarationId(d.getId());
        dp.setUserId(user.getId());
        dp.setRole(role);
        dp.setDisplayName(user.getDisplayName());
        participantRepository.save(dp);
    }

    private User firstUser(Role role, Long merchantId) {
        if (role == Role.MERCHANT && merchantId != null) {
            return userRepository.findFirstByRole(Role.MERCHANT)
                    .filter(u -> Objects.equals(u.getMerchantId(), merchantId))
                    .or(() -> userRepository.findFirstByRole(Role.MERCHANT)).orElse(null);
        }
        return userRepository.findFirstByRole(role).orElse(null);
    }

    // ---------------- 提交海关 ----------------

    @Transactional
    public Declaration submit(Long declarationId, User actor) {
        Declaration d = getDeclaration(declarationId);
        if (d.getStatus() != DeclarationStatus.DRAFT && d.getStatus() != DeclarationStatus.SUPPLEMENT_REQUIRED) {
            throw new BizException("当前申报单状态不允许提交: " + d.getStatus());
        }
        Parcel p = parcelRepository.findById(d.getParcelId()).orElseThrow();
        boolean resubmit = d.getStatus() == DeclarationStatus.SUPPLEMENT_REQUIRED;
        d.setStatus(DeclarationStatus.SUBMITTED);
        d.setSubmittedBy(actor.getDisplayName());
        d.setSubmittedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        declarationRepository.save(d);

        PackageStatus old = p.getStatus();
        p.setStatus(PackageStatus.CUSTOMS_REVIEW);
        p.setCustomsDelayed(false);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);

        // 海关系统异步受理（模拟海关系统延迟）
        CustomsTask task = new CustomsTask();
        task.setDeclarationId(d.getId());
        task.setTaskType("REVIEW");
        task.setExecuteAfter(LocalDateTime.now().plusSeconds(customsDelaySeconds));
        customsTaskRepository.save(task);

        eventService.record(p.getId(), old, PackageStatus.CUSTOMS_REVIEW, "申报提交", actor,
                (resubmit ? "补充材料后重新提交海关，" : "") + "申报单 " + d.getDeclarationNo()
                        + " 已提交海关审单，预计 " + customsDelaySeconds + " 秒内回执");
        return d;
    }

    // ---------------- 补材料 / 重新提交 / 拒绝补材料 ----------------

    /** 包裹级材料上传（申报前传票，如高风险商家提前上传发票） */
    @Transactional
    public Material uploadParcelMaterial(Long parcelId, Dtos.MaterialUploadRequest req, User actor) {
        Parcel p = parcelRepository.findById(parcelId).orElseThrow(() -> BizException.notFound("包裹"));
        Material m = new Material();
        m.setParcelId(p.getId());
        m.setMaterialType(MaterialType.valueOf(req.materialType()));
        m.setFileName(req.fileName());
        m.setFileUrl(req.fileUrl());
        m.setUploadedBy(actor.getDisplayName());
        materialRepository.save(m);
        eventService.record(parcelId, null, p.getStatus(), "材料上传", actor,
                "上传" + materialTypeName(m.getMaterialType()) + "：" + req.fileName());
        return m;
    }

    @Transactional
    public Material uploadMaterial(Long declarationId, Dtos.MaterialUploadRequest req, User actor) {
        Declaration d = getDeclaration(declarationId);
        Material m = new Material();
        m.setParcelId(d.getParcelId());
        m.setDeclarationId(d.getId());
        m.setMaterialType(MaterialType.valueOf(req.materialType()));
        m.setFileName(req.fileName());
        m.setFileUrl(req.fileUrl());
        m.setUploadedBy(actor.getDisplayName());
        materialRepository.save(m);
        eventService.record(d.getParcelId(), null, parcelRepository.findById(d.getParcelId()).orElseThrow().getStatus(),
                "材料上传", actor, "上传" + materialTypeName(m.getMaterialType()) + "：" + req.fileName());
        return m;
    }

    /** 商家拒绝补材料：申报单转入扣留，商家违规次数 +1 */
    @Transactional
    public Declaration refuseSupplement(Long declarationId, User actor) {
        Declaration d = getDeclaration(declarationId);
        if (d.getStatus() != DeclarationStatus.SUPPLEMENT_REQUIRED) {
            throw new BizException("该申报单不在补材料状态");
        }
        Parcel p = parcelRepository.findById(d.getParcelId()).orElseThrow();
        Merchant m = merchantRepository.findById(d.getMerchantId()).orElseThrow();
        m.setViolationCount(m.getViolationCount() + 1);
        merchantRepository.save(m);

        d.setStatus(DeclarationStatus.FAILED_DETAINED);
        d.setUpdatedAt(LocalDateTime.now());
        declarationRepository.save(d);

        PackageStatus old = p.getStatus();
        p.setStatus(PackageStatus.DETAINED);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);

        eventService.record(p.getId(), old, PackageStatus.DETAINED, "拒绝补材料", actor,
                "商家拒绝补充海关要求的材料，包裹扣留处理；商家违规次数累计 " + m.getViolationCount() + " 次");
        return d;
    }

    // ---------------- 放行（海关接受 + 税费缴清） ----------------

    @Transactional
    public void tryRelease(Long declarationId, User actor) {
        Declaration d = getDeclaration(declarationId);
        if (d.getStatus() != DeclarationStatus.ACCEPTED && d.getStatus() != DeclarationStatus.INSPECTION_PASSED) {
            return;
        }
        List<TaxRecord> taxes = taxRecordRepository.findByDeclarationId(declarationId);
        boolean allPaid = !taxes.isEmpty() && taxes.stream().allMatch(t -> t.getStatus() == TaxStatus.PAID);
        if (!allPaid) {
            return;
        }
        Parcel p = parcelRepository.findById(d.getParcelId()).orElseThrow();
        d.setStatus(DeclarationStatus.RELEASED);
        d.setUpdatedAt(LocalDateTime.now());
        declarationRepository.save(d);

        PackageStatus old = p.getStatus();
        p.setStatus(PackageStatus.RELEASED);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);
        eventService.record(p.getId(), old, PackageStatus.RELEASED, "海关放行", actor,
                "海关审结且税费缴清，包裹放行，可安排国内派送");
    }

    // ---------------- 查询 ----------------

    public Declaration getDeclaration(Long id) {
        return declarationRepository.findById(id).orElseThrow(() -> BizException.notFound("申报单"));
    }

    public Map<String, Object> detail(Long id) {
        Declaration d = getDeclaration(id);
        Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("declaration", d);
        v.put("parcel", parcelRepository.findById(d.getParcelId()).orElse(null));
        v.put("participants", participantRepository.findByDeclarationId(id));
        v.put("materials", materialRepository.findByDeclarationId(id));
        v.put("taxes", taxRecordRepository.findByDeclarationId(id));
        v.put("customsTasks", customsTaskRepository.findByDeclarationId(id));
        return v;
    }

    public List<Declaration> listForUser(User user) {
        if (user.getRole() == Role.MERCHANT) {
            return declarationRepository.findByMerchantIdOrderByCreatedAtDesc(user.getMerchantId());
        }
        return declarationRepository.findAllByOrderByCreatedAtDesc();
    }

    public static String genNo(String prefix) {
        return prefix + System.currentTimeMillis() + String.format("%03d", new java.util.Random().nextInt(1000));
    }

    public static String materialTypeName(MaterialType t) {
        return switch (t) {
            case INVOICE -> "发票";
            case CERT -> "认证证明";
            case PHOTO -> "照片";
            case EXPLANATION -> "情况说明";
            case OTHER -> "其他材料";
        };
    }
}
