package com.port.inspection.service;

import com.port.inspection.dto.Dtos;
import com.port.inspection.exception.BizException;
import com.port.inspection.model.*;
import com.port.inspection.model.enums.*;
import com.port.inspection.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 申报价格异常复核。
 * 预检发现某品牌商品申报价远低于同品牌同类历史成交价时自动立案，商家上传
 * 采购凭证/促销说明/付款记录三证，报关员复核给出“继续申报 / 补税 / 转人工查验”结论。
 * 结论影响商家风险等级，并沉淀到同品牌同类规则，后续申报提前提示、更严格预审。
 */
@Service
@RequiredArgsConstructor
public class PriceReviewService {

    /** 价格复核必备三证 */
    public static final List<MaterialType> REQUIRED_EVIDENCE = List.of(
            MaterialType.PURCHASE_PROOF, MaterialType.PROMO_EXPLANATION, MaterialType.PAYMENT_RECORD);

    private final PriceReviewOrderRepository reviewRepository;
    private final MaterialRepository materialRepository;
    private final ParcelRepository parcelRepository;
    private final DeclarationRepository declarationRepository;
    private final TaxRecordRepository taxRecordRepository;
    private final MerchantRepository merchantRepository;
    private final InspectionOrderRepository inspectionOrderRepository;
    private final BrandPriceRuleService brandRuleService;
    private final TaxCalculator taxCalculator;
    private final ParcelEventService eventService;

    // ---------------- 预检比对与立案 ----------------

    /**
     * 同品牌同类历史成交价比对，作为申报前检查的一项；命中“远低于”时自动立案。
     */
    @Transactional
    public PrecheckResult evaluate(Parcel p, User actor) {
        PrecheckResult r = new PrecheckResult();
        r.setParcelId(p.getId());
        r.setCheckType(CheckType.BRAND_PRICE);

        if (p.getBrand() == null || p.getBrand().isBlank()) {
            r.setLevel(PrecheckLevel.PASS);
            r.setMessage("未填写品牌，不参与同品牌历史成交价比对");
            return r;
        }
        BrandPriceRule rule = brandRuleService.find(p.getBrand(), p.getHsCode());
        if (rule == null) {
            r.setLevel(PrecheckLevel.PASS);
            r.setMessage("品牌「" + p.getBrand() + "」该品类暂无历史成交价记录");
            return r;
        }
        boolean alreadyReviewed = reviewRepository.findByParcelIdOrderByCreatedAtDesc(p.getId()).stream()
                .anyMatch(o -> o.getStatus() == PriceReviewStatus.COMPLETED);
        boolean openReview = reviewRepository.existsByParcelIdAndStatusIn(p.getId(),
                List.of(PriceReviewStatus.AWAITING_EVIDENCE, PriceReviewStatus.UNDER_REVIEW));

        BigDecimal lowCut = rule.getAvgDealPrice().multiply(rule.getLowReportThreshold());
        BigDecimal warnCut = rule.getAvgDealPrice().multiply(rule.getWarnThreshold());
        boolean watched = brandRuleService.isWatched(p.getMerchantId(), p.getBrand(), p.getHsCode());
        String base = "品牌「" + p.getBrand() + "」同类历史成交均价 ¥" + rule.getAvgDealPrice()
                + "，本单申报单价 ¥" + p.getDeclaredPrice();

        if (alreadyReviewed) {
            r.setLevel(PrecheckLevel.PASS);
            r.setMessage(base + "；已完成价格复核，按复核结论办理");
            return r;
        }
        // 远低于立案阈值，或被列入重点复核的商家低于预警阈值 → 立案阻断
        boolean farLow = p.getDeclaredPrice().compareTo(lowCut) < 0;
        boolean strictLow = (watched || Boolean.TRUE.equals(rule.getReviewFlag()))
                && p.getDeclaredPrice().compareTo(warnCut) < 0;
        if ((farLow || strictLow) && !openReview) {
            createReview(p, rule, actor);
            r.setLevel(PrecheckLevel.FAIL);
            r.setMessage(base + (farLow ? "，远低于历史成交价" : "，重点复核商家低于预警线")
                    + "，须上传采购凭证、促销说明、付款记录，经报关员价格复核后方可继续申报");
            return r;
        }
        if (openReview) {
            r.setLevel(PrecheckLevel.FAIL);
            r.setMessage(base + "；存在未完成的价格复核单，须补齐三证并经报关员复核");
            return r;
        }
        if (p.getDeclaredPrice().compareTo(warnCut) < 0
                || watched || Boolean.TRUE.equals(rule.getReviewFlag())) {
            r.setLevel(PrecheckLevel.WARN);
            r.setMessage(base + "；" + (watched ? "商家在该品牌品类重点复核名单，" : "")
                    + (Boolean.TRUE.equals(rule.getReviewFlag()) ? "该品类近期存低报记录，" : "")
                    + "申报价偏低，请提前准备采购与付款凭证");
            return r;
        }
        r.setLevel(PrecheckLevel.PASS);
        r.setMessage(base + "，处于历史成交价合理区间");
        return r;
    }

    private PriceReviewOrder createReview(Parcel p, BrandPriceRule rule, User actor) {
        PriceReviewOrder o = new PriceReviewOrder();
        o.setReviewNo(DeclarationService.genNo("PRV"));
        o.setParcelId(p.getId());
        o.setMerchantId(p.getMerchantId());
        o.setBrand(p.getBrand().trim());
        o.setHsCode(p.getHsCode());
        o.setDeclaredPrice(p.getDeclaredPrice());
        o.setReferenceAvgPrice(rule.getAvgDealPrice());
        o.setRequestedBy(actor == null ? "系统预检" : actor.getDisplayName());
        declarationRepository.findByParcelId(p.getId()).stream().findFirst()
                .ifPresent(d -> o.setDeclarationId(d.getId()));
        reviewRepository.save(o);
        eventService.record(p.getId(), p.getStatus(), p.getStatus(), "价格复核立案", actor,
                "品牌「" + o.getBrand() + "」申报价 ¥" + p.getDeclaredPrice()
                        + " 远低于历史成交均价 ¥" + rule.getAvgDealPrice()
                        + "，复核单 " + o.getReviewNo() + "，待商家上传采购凭证/促销说明/付款记录");
        return o;
    }

    // ---------------- 商家上传三证 ----------------

    @Transactional
    public Material uploadEvidence(Long id, Dtos.MaterialUploadRequest req, User actor) {
        PriceReviewOrder o = getOrder(id);
        if (actor.getRole() == Role.MERCHANT && !Objects.equals(o.getMerchantId(), actor.getMerchantId())) {
            throw BizException.forbidden("只能为本商家的复核单上传凭证");
        }
        if (o.getStatus() == PriceReviewStatus.COMPLETED) {
            throw new BizException("该价格复核单已完成，不能再上传凭证");
        }
        MaterialType type = MaterialType.valueOf(req.materialType());
        if (!REQUIRED_EVIDENCE.contains(type)) {
            throw new BizException("价格复核凭证仅支持：采购凭证 PURCHASE_PROOF / 促销说明 PROMO_EXPLANATION / 付款记录 PAYMENT_RECORD");
        }
        Material m = new Material();
        m.setParcelId(o.getParcelId());
        m.setDeclarationId(o.getDeclarationId());
        m.setMaterialType(type);
        m.setFileName(req.fileName());
        m.setFileUrl(req.fileUrl());
        m.setUploadedBy(actor.getDisplayName());
        materialRepository.save(m);

        List<MaterialType> missing = missingEvidence(o.getParcelId());
        if (missing.isEmpty() && o.getStatus() == PriceReviewStatus.AWAITING_EVIDENCE) {
            o.setStatus(PriceReviewStatus.UNDER_REVIEW);
            reviewRepository.save(o);
        }
        String note = "上传价格复核凭证「" + DeclarationService.materialTypeName(type) + "」"
                + (missing.isEmpty() ? "，三证齐备，转报关员复核" : "，仍缺：" + missing.stream()
                .map(DeclarationService::materialTypeName).reduce((a, b) -> a + "、" + b).orElse(""));
        eventService.record(o.getParcelId(), null,
                parcelRepository.findById(o.getParcelId()).orElseThrow().getStatus(),
                "复核材料上传", actor, note + "（复核单 " + o.getReviewNo() + "）");
        return m;
    }

    private List<MaterialType> missingEvidence(Long parcelId) {
        List<MaterialType> have = materialRepository.findByParcelId(parcelId).stream()
                .map(Material::getMaterialType).toList();
        List<MaterialType> missing = new ArrayList<>();
        for (MaterialType t : REQUIRED_EVIDENCE) {
            if (!have.contains(t)) missing.add(t);
        }
        return missing;
    }

    // ---------------- 报关员复核结论 ----------------

    @Transactional
    public PriceReviewOrder decide(Long id, Dtos.PriceReviewDecisionRequest req, User actor) {
        PriceReviewOrder o = getOrder(id);
        if (o.getStatus() == PriceReviewStatus.COMPLETED) {
            throw new BizException("该价格复核单已出具结论");
        }
        List<MaterialType> missing = missingEvidence(o.getParcelId());
        if (!missing.isEmpty()) {
            throw new BizException("三证不齐，不能复核，仍缺：" + missing.stream()
                    .map(DeclarationService::materialTypeName).reduce((a, b) -> a + "、" + b).orElse(""));
        }
        PriceReviewDecision decision = PriceReviewDecision.valueOf(req.decision());
        Parcel p = parcelRepository.findById(o.getParcelId()).orElseThrow();
        Merchant m = merchantRepository.findById(o.getMerchantId()).orElseThrow();

        o.setStatus(PriceReviewStatus.COMPLETED);
        o.setDecision(decision);
        o.setDecidedBy(actor.getDisplayName());
        o.setDecidedAt(LocalDateTime.now());
        o.setDecisionNote(req.note());

        switch (decision) {
            case PASS -> applyPass(o, p, m, actor);
            case SUPPLEMENT_TAX -> applySupplement(o, p, m, req.revisedUnitPrice(), actor);
            case MANUAL_INSPECTION -> applyManual(o, p, m, actor);
        }
        reviewRepository.save(o);
        return o;
    }

    /** 结论一：价格合理，继续申报；不加重风险，以申报价沉淀为可信成交价 */
    private void applyPass(PriceReviewOrder o, Parcel p, Merchant m, User actor) {
        brandRuleService.applyReviewOutcome(o.getBrand(), o.getHsCode(), null,
                PriceReviewDecision.PASS.name(), p.getDeclaredPrice(), m.getId());
        eventService.record(p.getId(), p.getStatus(), p.getStatus(), "价格复核结论", actor,
                "复核单 " + o.getReviewNo() + " 结论：凭证齐备、价格合理，继续申报；商家风险等级维持 "
                        + m.getRiskLevel());
    }

    /** 结论二：按认定成交价补税，重算计税价/税费；风险上调一级 */
    private void applySupplement(PriceReviewOrder o, Parcel p, Merchant m, BigDecimal revisedInput, User actor) {
        BigDecimal revised = revisedInput != null ? revisedInput
                : (o.getReferenceAvgPrice() != null ? o.getReferenceAvgPrice() : p.getDeclaredPrice());
        o.setRevisedUnitPrice(revised);
        p.setTaxablePrice(revised);
        parcelRepository.save(p);

        // 重算申报单税费：更新未缴税费金额
        String taxInfo = "";
        if (o.getDeclarationId() != null) {
            Declaration d = declarationRepository.findById(o.getDeclarationId()).orElse(null);
            if (d != null) {
                BigDecimal newTax = taxCalculator.computeTax(p);
                d.setTaxAmount(newTax);
                declarationRepository.save(d);
                for (TaxRecord t : taxRecordRepository.findByDeclarationId(d.getId())) {
                    if (t.getStatus() == TaxStatus.PENDING) {
                        t.setAmount(newTax);
                        taxRecordRepository.save(t);
                    }
                }
                taxInfo = "，申报单 " + d.getDeclarationNo() + " 应缴税费调整为 ¥" + newTax;
            }
        }
        RiskChange rc = escalate(m, false);
        brandRuleService.applyReviewOutcome(o.getBrand(), o.getHsCode(), null,
                PriceReviewDecision.SUPPLEMENT_TAX.name(), revised, m.getId());
        eventService.record(p.getId(), p.getStatus(), p.getStatus(), "价格复核结论", actor,
                "复核单 " + o.getReviewNo() + " 结论：申报价偏低，按认定单价 ¥" + revised + " 补税" + taxInfo);
        eventService.record(p.getId(), p.getStatus(), p.getStatus(), "风险等级调整", actor,
                "低报价格补税，商家风险 " + rc.from() + "→" + m.getRiskLevel()
                        + "，抽检比例调整为 " + m.getInspectionRatio() + "%，同品牌同类后续严格预审");
    }

    /** 结论三：转人工查验；风险直接上调至高风险 */
    private void applyManual(PriceReviewOrder o, Parcel p, Merchant m, User actor) {
        RiskChange rc = escalate(m, true);
        brandRuleService.applyReviewOutcome(o.getBrand(), o.getHsCode(), null,
                PriceReviewDecision.MANUAL_INSPECTION.name(), o.getReferenceAvgPrice(), m.getId());

        Declaration d = o.getDeclarationId() == null ? null
                : declarationRepository.findById(o.getDeclarationId()).orElse(null);
        if (d != null) {
            openInspection(o, p, d, actor);
        } else {
            // 尚未创建申报单：保持“可申报”，创建申报单时自动布控人工查验（见 hook）
            eventService.record(p.getId(), p.getStatus(), p.getStatus(), "价格复核结论", actor,
                    "复核单 " + o.getReviewNo() + " 结论：转人工查验，创建申报单后将自动布控");
        }
        eventService.record(p.getId(), p.getStatus(), p.getStatus(), "风险等级调整", actor,
                "低报价格转人工查验，商家风险 " + rc.from() + "→" + m.getRiskLevel()
                        + "，抽检比例调整为 " + m.getInspectionRatio() + "%，纳入重点复核名单");
    }

    /** 创建申报单时，对“已转人工但尚无申报单”的复核单补开人工查验 */
    @Transactional
    public void hookManualInspectionAfterDeclarationCreated(Declaration d, Parcel p) {
        boolean needManual = reviewRepository.findByParcelIdOrderByCreatedAtDesc(p.getId()).stream()
                .anyMatch(o -> o.getStatus() == PriceReviewStatus.COMPLETED
                        && o.getDecision() == PriceReviewDecision.MANUAL_INSPECTION);
        if (!needManual) return;
        boolean hasInspection = !inspectionOrderRepository.findByParcelId(p.getId()).isEmpty();
        if (hasInspection) return;
        PriceReviewOrder o = reviewRepository.findByParcelIdOrderByCreatedAtDesc(p.getId()).stream()
                .filter(x -> x.getDecision() == PriceReviewDecision.MANUAL_INSPECTION).findFirst().orElseThrow();
        o.setDeclarationId(d.getId());
        reviewRepository.save(o);
        openInspection(o, p, d, null);
    }

    private void openInspection(PriceReviewOrder o, Parcel p, Declaration d, User actor) {
        d.setStatus(DeclarationStatus.INSPECTION_REQUIRED);
        d.setUpdatedAt(LocalDateTime.now());
        declarationRepository.save(d);

        InspectionOrder order = new InspectionOrder();
        order.setOrderNo(DeclarationService.genNo("INS"));
        order.setDeclarationId(d.getId());
        order.setParcelId(p.getId());
        order.setInstruction("价格异常转人工查验：核对品牌/成交价，开箱核验采购凭证与实物，复核单 " + o.getReviewNo());
        order.setIssuedBy("价格复核(" + (actor == null ? "报关员" : actor.getDisplayName()) + ")");
        inspectionOrderRepository.save(order);

        PackageStatus old = p.getStatus();
        p.setStatus(PackageStatus.INSPECTION);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);
        eventService.record(p.getId(), old, PackageStatus.INSPECTION, "海关布控", actor,
                "价格复核转人工查验，下达查验指令 " + order.getOrderNo());
    }

    private record RiskChange(RiskLevel from) {}

    private RiskChange escalate(Merchant m, boolean toHigh) {
        RiskLevel from = m.getRiskLevel();
        m.setViolationCount(m.getViolationCount() + 1);
        if (toHigh || from == RiskLevel.MEDIUM) {
            m.setRiskLevel(RiskLevel.HIGH);
            m.setInspectionRatio(Math.min(100, Math.max(m.getInspectionRatio(), 50)));
            m.setRequireAdvanceDocs(true);
        } else if (from == RiskLevel.LOW) {
            m.setRiskLevel(RiskLevel.MEDIUM);
            m.setInspectionRatio(Math.min(100, Math.max(m.getInspectionRatio(), 30)));
        } else {
            m.setInspectionRatio(Math.min(100, m.getInspectionRatio() + 15));
        }
        merchantRepository.save(m);
        return new RiskChange(from);
    }

    // ---------------- 查询 ----------------

    /** 存在未完成（待传凭证 / 待复核）的价格复核单时，禁止创建申报单 */
    public void assertNoOpenReview(Long parcelId) {
        boolean open = reviewRepository.existsByParcelIdAndStatusIn(parcelId,
                List.of(PriceReviewStatus.AWAITING_EVIDENCE, PriceReviewStatus.UNDER_REVIEW));
        if (open) {
            throw new BizException("该包裹存在未完成的价格复核单：须上传采购凭证、促销说明、付款记录三证，"
                    + "经报关员复核后才能继续申报");
        }
    }

    public boolean hasOpenReview(Long parcelId) {
        return reviewRepository.existsByParcelIdAndStatusIn(parcelId,
                List.of(PriceReviewStatus.AWAITING_EVIDENCE, PriceReviewStatus.UNDER_REVIEW));
    }

    public PriceReviewOrder getOrder(Long id) {
        return reviewRepository.findById(id).orElseThrow(() -> BizException.notFound("价格复核单"));
    }

    public List<PriceReviewOrder> list(String status, User user) {
        List<PriceReviewOrder> all = (status != null && !status.isBlank())
                ? reviewRepository.findByStatus(PriceReviewStatus.valueOf(status))
                : reviewRepository.findAllByOrderByCreatedAtDesc();
        if (user.getRole() == Role.MERCHANT) {
            return all.stream().filter(o -> Objects.equals(o.getMerchantId(), user.getMerchantId())).toList();
        }
        return all;
    }

    public List<Material> evidenceMaterials(Long parcelId) {
        return materialRepository.findByParcelId(parcelId).stream()
                .filter(x -> REQUIRED_EVIDENCE.contains(x.getMaterialType())).toList();
    }
}
