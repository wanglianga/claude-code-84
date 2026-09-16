package com.port.inspection.service;

import com.port.inspection.dto.Dtos;
import com.port.inspection.exception.BizException;
import com.port.inspection.model.*;
import com.port.inspection.model.enums.*;
import com.port.inspection.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** 退运/销毁处置：申请 → 海关核准 → 仓库执行 */
@Service
@RequiredArgsConstructor
public class ReturnService {

    private final ReturnOrderRepository returnOrderRepository;
    private final ParcelRepository parcelRepository;
    private final DeclarationRepository declarationRepository;
    private final TaxRecordRepository taxRecordRepository;
    private final ParcelEventService eventService;

    /** 商家/客服发起退运申请 */
    @Transactional
    public ReturnOrder request(Long parcelId, Dtos.ReturnApplyRequest req, User actor) {
        Parcel p = parcelRepository.findById(parcelId).orElseThrow(() -> BizException.notFound("包裹"));
        if (actor.getRole() == Role.MERCHANT && !Objects.equals(p.getMerchantId(), actor.getMerchantId())) {
            throw BizException.forbidden("只能操作本商家的包裹");
        }
        EnumSet<PackageStatus> allowed = EnumSet.of(PackageStatus.RECEIVED, PackageStatus.PRECHECK_PASSED,
                PackageStatus.PRECHECK_FAILED, PackageStatus.HOLD, PackageStatus.DECLARED,
                PackageStatus.CUSTOMS_REVIEW, PackageStatus.SUPPLEMENT_REQUIRED, PackageStatus.DETAINED);
        if (!allowed.contains(p.getStatus())) {
            throw new BizException("当前状态不允许申请退运/销毁: " + p.getStatus());
        }
        ReturnType type = ReturnType.valueOf(req.type());
        ReturnOrder ro = new ReturnOrder();
        ro.setReturnNo(DeclarationService.genNo(type == ReturnType.RETURN ? "RTN" : "DST"));
        ro.setParcelId(parcelId);
        ro.setType(type);
        ro.setReason(req.reason());
        ro.setRequestedBy(actor.getDisplayName());
        declarationRepository.findByParcelId(parcelId).stream().findFirst()
                .ifPresent(d -> ro.setDeclarationId(d.getId()));
        returnOrderRepository.save(ro);
        eventService.record(parcelId, p.getStatus(), p.getStatus(),
                type == ReturnType.RETURN ? "退运申请" : "销毁申请", actor,
                "原因：" + req.reason() + "，处置单 " + ro.getReturnNo() + " 待海关核准");
        return ro;
    }

    /** 海关核准 */
    @Transactional
    public ReturnOrder approve(Long id, User actor) {
        ReturnOrder ro = getOrder(id);
        if (ro.getStatus() != ReturnStatus.REQUESTED) {
            throw new BizException("该处置单不在待核准状态");
        }
        ro.setStatus(ReturnStatus.APPROVED);
        ro.setApprovedBy(actor.getDisplayName());
        returnOrderRepository.save(ro);
        Parcel p = parcelRepository.findById(ro.getParcelId()).orElseThrow();
        if (ro.getType() == ReturnType.RETURN) {
            PackageStatus old = p.getStatus();
            p.setStatus(PackageStatus.RETURNING);
            p.setUpdatedAt(LocalDateTime.now());
            parcelRepository.save(p);
            eventService.record(p.getId(), old, p.getStatus(), "退运核准", actor,
                    "海关核准退运，处置单 " + ro.getReturnNo());
        } else {
            eventService.record(p.getId(), p.getStatus(), p.getStatus(), "销毁核准", actor,
                    "海关核准销毁，处置单 " + ro.getReturnNo());
        }
        return ro;
    }

    /** 海关驳回 */
    @Transactional
    public ReturnOrder reject(Long id, User actor) {
        ReturnOrder ro = getOrder(id);
        if (ro.getStatus() != ReturnStatus.REQUESTED) {
            throw new BizException("该处置单不在待核准状态");
        }
        ro.setStatus(ReturnStatus.REJECTED);
        ro.setApprovedBy(actor.getDisplayName());
        returnOrderRepository.save(ro);
        eventService.record(ro.getParcelId(), null, parcelRepository.findById(ro.getParcelId()).orElseThrow().getStatus(),
                "处置驳回", actor, "海关驳回处置单 " + ro.getReturnNo());
        return ro;
    }

    /** 仓库执行退运/销毁 */
    @Transactional
    public ReturnOrder execute(Long id, User actor) {
        ReturnOrder ro = getOrder(id);
        if (ro.getStatus() != ReturnStatus.APPROVED) {
            throw new BizException("处置单须先经海关核准");
        }
        // 终态守卫：包裹已处于退运/销毁终态时拒绝重复执行，保证“已缴税费只退一次”
        Parcel parcel = parcelRepository.findById(ro.getParcelId()).orElseThrow();
        if (parcel.getStatus() == PackageStatus.RETURNED || parcel.getStatus() == PackageStatus.DESTROYED) {
            throw new BizException("包裹已完成" + (parcel.getStatus() == PackageStatus.DESTROYED ? "销毁" : "退运")
                    + "处置，终态不可重复执行");
        }
        ro.setStatus(ReturnStatus.COMPLETED);
        ro.setCompletedAt(LocalDateTime.now());
        returnOrderRepository.save(ro);

        PackageStatus old = parcel.getStatus();
        boolean isReturn = ro.getType() == ReturnType.RETURN;
        PackageStatus done = isReturn ? PackageStatus.RETURNED : PackageStatus.DESTROYED;
        parcel.setStatus(done);
        parcel.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(parcel);

        // 同一申报单统一终态处理：已缴税费仅退一次；未缴税费转为不可缴纳的作废状态并保留原因
        settleTaxes(ro, parcel, done, actor);

        // 申报单随包裹进入退运/销毁终态，后续缴税不得再将其改回 RELEASED
        DeclarationStatus decDone = isReturn ? DeclarationStatus.RETURNED : DeclarationStatus.DESTROYED;
        if (ro.getDeclarationId() != null) {
            declarationRepository.findById(ro.getDeclarationId()).ifPresent(d -> {
                d.setStatus(decDone);
                d.setUpdatedAt(LocalDateTime.now());
                declarationRepository.save(d);
            });
        } else {
            declarationRepository.findByParcelId(parcel.getId()).stream().findFirst().ifPresent(d -> {
                ro.setDeclarationId(d.getId());
                d.setStatus(decDone);
                d.setUpdatedAt(LocalDateTime.now());
                declarationRepository.save(d);
            });
        }

        eventService.record(parcel.getId(), old, done,
                isReturn ? "退运执行" : "销毁执行", actor,
                (isReturn ? "包裹已退运出境" : "包裹已按海关要求销毁")
                        + "，处置单 " + ro.getReturnNo() + "；税费清算：" + taxSettlementSummary(ro, parcel));
        return ro;
    }

    /**
     * 处置完成后的税费清算（围绕同一申报单）：
     * PAID     → REFUNDED（仅一次，事件留痕）；
     * PENDING  → VOID（不可再缴纳，记录作废原因）；
     * REFUNDED → 保持（历史已退，不重复退款）；
     * VOID     → 保持（历史已作废）。
     */
    private void settleTaxes(ReturnOrder ro, Parcel p, PackageStatus done, User actor) {
        String action = ro.getType() == ReturnType.RETURN ? "退运" : "销毁";
        String voidReason = action + "处置完成（处置单 " + ro.getReturnNo() + "，原因：" + ro.getReason()
                + "），包裹已" + (ro.getType() == ReturnType.RETURN ? "退运出境" : "销毁")
                + "，税费义务取消，未缴税费作废、不再缴纳";
        for (TaxRecord t : taxRecordRepository.findByParcelId(p.getId())) {
            if (t.getStatus() == TaxStatus.PAID) {
                t.setStatus(TaxStatus.REFUNDED);
                taxRecordRepository.save(t);
                eventService.record(p.getId(), done, done, "税费退还", actor,
                        action + "处置完成，已缴税费 ¥" + t.getAmount() + " 原路退回（仅退一次），税单 " + t.getId());
            } else if (t.getStatus() == TaxStatus.PENDING) {
                t.setStatus(TaxStatus.VOID);
                t.setVoidReason(voidReason);
                taxRecordRepository.save(t);
                eventService.record(p.getId(), done, done, "税费作废", actor,
                        "未缴" + t.getTaxType() + " ¥" + t.getAmount() + " 随" + action
                                + "终态作废，不可再缴纳，税单 " + t.getId());
            }
        }
    }

    /** 处置执行事件中的税费清算结论摘要 */
    private String taxSettlementSummary(ReturnOrder ro, Parcel p) {
        int refunded = 0, voided = 0;
        for (TaxRecord t : taxRecordRepository.findByParcelId(p.getId())) {
            if (t.getStatus() == TaxStatus.REFUNDED) refunded++;
            else if (t.getStatus() == TaxStatus.VOID) voided++;
        }
        StringBuilder sb = new StringBuilder();
        if (refunded > 0) sb.append("已缴税费退款 ").append(refunded).append(" 笔");
        if (voided > 0) {
            if (refunded > 0) sb.append("；");
            sb.append("未缴税费作废 ").append(voided).append(" 笔（不可缴纳）");
        }
        if (refunded == 0 && voided == 0) sb.append("无在途税费");
        return sb.toString();
    }

    public ReturnOrder getOrder(Long id) {
        return returnOrderRepository.findById(id).orElseThrow(() -> BizException.notFound("处置单"));
    }

    public List<ReturnOrder> list(String status) {
        if (status != null && !status.isBlank()) {
            return returnOrderRepository.findByStatus(ReturnStatus.valueOf(status));
        }
        return returnOrderRepository.findAllByOrderByCreatedAtDesc();
    }
}
