package com.port.inspection.service;

import com.port.inspection.exception.BizException;
import com.port.inspection.model.*;
import com.port.inspection.model.enums.*;
import com.port.inspection.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 申报前检查：商品禁限售、价格异常、身份证重复、税费规则、同收件人频次、商家历史风险。
 */
@Service
@RequiredArgsConstructor
public class PrecheckService {

    /** 个人物品单次交易限值 */
    public static final BigDecimal PERSONAL_LIMIT = new BigDecimal("5000");

    private final PrecheckResultRepository precheckRepository;
    private final RestrictedGoodsRepository restrictedGoodsRepository;
    private final TaxRuleRepository taxRuleRepository;
    private final ParcelRepository parcelRepository;
    private final MerchantRepository merchantRepository;
    private final TaxCalculator taxCalculator;
    private final PriceReviewService priceReviewService;
    private final ParcelEventService eventService;

    @Transactional
    public List<PrecheckResult> runPrecheck(Long parcelId, User actor) {
        Parcel p = parcelRepository.findById(parcelId)
                .orElseThrow(() -> BizException.notFound("包裹"));
        if (p.getStatus() != PackageStatus.RECEIVED && p.getStatus() != PackageStatus.PRECHECK_FAILED
                && p.getStatus() != PackageStatus.HOLD && p.getStatus() != PackageStatus.PRECHECK_PASSED) {
            throw new BizException("当前状态不允许执行申报前检查: " + p.getStatus());
        }
        precheckRepository.deleteByParcelId(parcelId);

        List<PrecheckResult> results = new ArrayList<>();
        results.add(checkRestrictedGoods(p));
        results.add(checkPrice(p));
        results.add(checkIdCard(p));
        results.add(checkTax(p));
        results.add(checkRecipientFrequency(p));
        results.add(checkMerchantRisk(p));
        // 第 7 项：同品牌同类商品历史成交价复核（命中远低阈值会自动立案）
        results.add(priceReviewService.evaluate(p, actor));
        precheckRepository.saveAll(results);

        boolean failed = results.stream().anyMatch(r -> r.getLevel() == PrecheckLevel.FAIL);
        PackageStatus old = p.getStatus();
        p.setStatus(failed ? PackageStatus.PRECHECK_FAILED : PackageStatus.PRECHECK_PASSED);
        p.setUpdatedAt(LocalDateTime.now());
        parcelRepository.save(p);

        long warns = results.stream().filter(r -> r.getLevel() == PrecheckLevel.WARN).count();
        String remark = failed ? "申报前检查未通过，禁止申报"
                : (warns > 0 ? "申报前检查通过（含 " + warns + " 项预警）" : "申报前检查全部通过");
        eventService.record(p.getId(), old, p.getStatus(), "申报前检查", actor, remark);
        return results;
    }

    /** 1. 商品禁限售检查 */
    private PrecheckResult checkRestrictedGoods(Parcel p) {
        PrecheckResult r = newResult(p, CheckType.RESTRICTED_GOODS);
        List<RestrictedGoods> rules = restrictedGoodsRepository.findAll();
        for (RestrictedGoods rule : rules) {
            boolean hit = (rule.getKeyword() != null && p.getGoodsName().contains(rule.getKeyword()))
                    || (rule.getHsCode() != null && p.getHsCode().startsWith(rule.getHsCode()));
            if (hit) {
                if ("PROHIBITED".equals(rule.getRuleType())) {
                    r.setLevel(PrecheckLevel.FAIL);
                    r.setMessage("禁止进口商品：" + rule.getDescription());
                } else {
                    r.setLevel(PrecheckLevel.WARN);
                    r.setMessage("限制进口商品：" + rule.getDescription() + "，需上传相关证明材料");
                }
                return r;
            }
        }
        r.setLevel(PrecheckLevel.PASS);
        r.setMessage("未命中禁限售目录");
        return r;
    }

    /** 2. 价格异常检查（对照税则参考价） */
    private PrecheckResult checkPrice(Parcel p) {
        PrecheckResult r = newResult(p, CheckType.PRICE_ANOMALY);
        Optional<TaxRule> rule = taxRuleRepository.findByHsCode(p.getHsCode());
        if (rule.isEmpty()) {
            r.setLevel(PrecheckLevel.WARN);
            r.setMessage("未匹配到税则号 " + p.getHsCode() + "，按默认税率计税，请人工复核价格");
            return r;
        }
        BigDecimal ref = rule.get().getRefPrice();
        BigDecimal price = p.getDeclaredPrice();
        if (price.compareTo(ref.multiply(new BigDecimal("0.5"))) < 0) {
            r.setLevel(PrecheckLevel.FAIL);
            r.setMessage("申报价格 ¥" + price + " 明显低于参考价 ¥" + ref + "（低于50%），涉嫌低报价格");
        } else if (price.compareTo(ref.multiply(new BigDecimal("5"))) > 0) {
            r.setLevel(PrecheckLevel.WARN);
            r.setMessage("申报价格 ¥" + price + " 显著高于参考价 ¥" + ref + "（高于5倍）");
        } else {
            r.setLevel(PrecheckLevel.PASS);
            r.setMessage("申报价格 ¥" + price + " 在参考价 ¥" + ref + " 合理区间内");
        }
        return r;
    }

    /** 3. 身份证重复检查（同一身份证被多个收件人使用） */
    private PrecheckResult checkIdCard(Parcel p) {
        PrecheckResult r = newResult(p, CheckType.ID_CARD_DUPLICATE);
        String idCard = p.getRecipientIdCard();
        if (!idCard.matches("\\d{17}[\\dXx]")) {
            r.setLevel(PrecheckLevel.FAIL);
            r.setMessage("身份证号码格式错误（须为18位）");
            return r;
        }
        long nameCount = parcelRepository.findByRecipientIdCard(idCard).stream()
                .map(Parcel::getRecipientName).distinct().count();
        boolean selfNew = parcelRepository.findByRecipientIdCard(idCard).stream()
                .noneMatch(x -> x.getId().equals(p.getId()));
        long total = nameCount + (selfNew ? 1 : 0);
        if (total > 2) {
            r.setLevel(PrecheckLevel.FAIL);
            r.setMessage("同一身份证被 " + total + " 个不同收件人使用，涉嫌盗用他人身份信息");
        } else {
            r.setLevel(PrecheckLevel.PASS);
            r.setMessage("身份证使用正常");
        }
        return r;
    }

    /** 4. 税费规则检查（计算预估税费，超限值提示转一般贸易） */
    private PrecheckResult checkTax(Parcel p) {
        PrecheckResult r = newResult(p, CheckType.TAX_RULE);
        BigDecimal total = p.getDeclaredPrice().multiply(BigDecimal.valueOf(p.getQuantity()));
        BigDecimal tax = computeTax(p);
        if (p.getTradeMode() == TradeMode.BONDED && total.compareTo(PERSONAL_LIMIT) > 0) {
            r.setLevel(PrecheckLevel.WARN);
            r.setMessage("包裹总价 ¥" + total + " 超出个人自用限值 ¥" + PERSONAL_LIMIT
                    + "，建议转一般贸易申报；预估税费 ¥" + tax);
        } else {
            r.setLevel(PrecheckLevel.PASS);
            r.setMessage("预估税费 ¥" + tax + "（" + (p.getTradeMode() == TradeMode.BONDED ? "跨境电商综合税" : "一般贸易税") + "）");
        }
        return r;
    }

    /** 5. 同收件人频次检查（7 天内超过 3 单） */
    private PrecheckResult checkRecipientFrequency(Parcel p) {
        PrecheckResult r = newResult(p, CheckType.RECIPIENT_FREQUENCY);
        long count = parcelRepository.countByRecipientIdCardAndCreatedAtAfter(
                p.getRecipientIdCard(), LocalDateTime.now().minusDays(7));
        if (count >= 3) {
            r.setLevel(PrecheckLevel.FAIL);
            r.setMessage("同一收件人 7 天内已有 " + count + " 个包裹，超出个人自用合理频次");
        } else {
            r.setLevel(PrecheckLevel.PASS);
            r.setMessage("收件人近 7 天包裹数 " + count + "，频次正常");
        }
        return r;
    }

    /** 6. 商家历史风险检查 */
    private PrecheckResult checkMerchantRisk(Parcel p) {
        PrecheckResult r = newResult(p, CheckType.MERCHANT_RISK);
        Merchant m = merchantRepository.findById(p.getMerchantId()).orElse(null);
        if (m == null) {
            r.setLevel(PrecheckLevel.FAIL);
            r.setMessage("商家不存在");
            return r;
        }
        if (m.getViolationCount() >= 5) {
            r.setLevel(PrecheckLevel.FAIL);
            r.setMessage("商家历史违规 " + m.getViolationCount() + " 次，已被限制申报");
        } else if (m.getRiskLevel() == RiskLevel.HIGH) {
            r.setLevel(PrecheckLevel.WARN);
            r.setMessage("高风险商家：抽检比例 " + m.getInspectionRatio() + "%"
                    + (Boolean.TRUE.equals(m.getRequireAdvanceDocs()) ? "，须提前上传完整票据" : "")
                    + "，历史违规 " + m.getViolationCount() + " 次");
        } else if (m.getRiskLevel() == RiskLevel.MEDIUM) {
            r.setLevel(PrecheckLevel.WARN);
            r.setMessage("中风险商家：抽检比例 " + m.getInspectionRatio() + "%");
        } else {
            r.setLevel(PrecheckLevel.PASS);
            r.setMessage("商家风险等级低，历史违规 " + m.getViolationCount() + " 次");
        }
        return r;
    }

    /** 按贸易模式计算税费（计税单价取价格复核补税认定价） */
    public BigDecimal computeTax(Parcel p) {
        return taxCalculator.computeTax(p);
    }

    private PrecheckResult newResult(Parcel p, CheckType type) {
        PrecheckResult r = new PrecheckResult();
        r.setParcelId(p.getId());
        r.setCheckType(type);
        return r;
    }
}
