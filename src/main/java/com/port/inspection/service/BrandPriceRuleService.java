package com.port.inspection.service;

import com.port.inspection.model.BrandPriceRule;
import com.port.inspection.model.MerchantPriceWatch;
import com.port.inspection.repository.BrandPriceRuleRepository;
import com.port.inspection.repository.MerchantPriceWatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 同品牌同类商品价格规则：价格复核结论沉淀于此，后续申报据此提前提示、更严格预审。
 */
@Service
@RequiredArgsConstructor
public class BrandPriceRuleService {

    private final BrandPriceRuleRepository ruleRepository;
    private final MerchantPriceWatchRepository watchRepository;

    public BrandPriceRule find(String brand, String hsCode) {
        if (brand == null || brand.isBlank()) return null;
        return ruleRepository.findByBrandAndHsCode(brand.trim(), hsCode).orElse(null);
    }

    public java.util.List<BrandPriceRule> listRules() {
        return ruleRepository.findAllByOrderByUpdatedAtDesc();
    }

    public java.util.List<MerchantPriceWatch> listWatches(Long merchantId) {
        return merchantId == null ? null : watchRepository.findByMerchantIdOrderByUpdatedAtDesc(merchantId);
    }

    /** 商家是否在该品牌品类的重点复核名单 */
    public boolean isWatched(Long merchantId, String brand, String hsCode) {
        if (brand == null || brand.isBlank()) return false;
        return watchRepository.findByMerchantIdAndBrandAndHsCode(merchantId, brand.trim(), hsCode)
                .map(MerchantPriceWatch::getStricterReview).orElse(false);
    }

    /**
     * 沉淀一条已确认成交价（放行/复核结论），滚动更新同品牌同类历史均价。
     */
    @Transactional
    public BrandPriceRule recordDeal(String brand, String hsCode, String category, BigDecimal unitPrice) {
        if (brand == null || brand.isBlank() || unitPrice == null) return null;
        BrandPriceRule rule = find(brand, hsCode);
        if (rule == null) {
            rule = new BrandPriceRule();
            rule.setBrand(brand.trim());
            rule.setHsCode(hsCode);
            rule.setCategory(category);
            rule.setAvgDealPrice(unitPrice);
            rule.setDealCount(1);
            return ruleRepository.save(rule);
        }
        int n = rule.getDealCount();
        BigDecimal total = rule.getAvgDealPrice().multiply(BigDecimal.valueOf(n)).add(unitPrice);
        rule.setDealCount(n + 1);
        rule.setAvgDealPrice(total.divide(BigDecimal.valueOf(n + 1L), 2, RoundingMode.HALF_UP));
        if (category != null && !category.isBlank()) rule.setCategory(category);
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    /**
     * 复核结论沉淀到品牌规则。
     * 关键：单票 PASS 只证明该票价格合理，仅把申报价滚动计入历史均价，
     * <b>不清除</b>既有的品类重点标记（reviewFlag）与商家重点复核名单（watch）；
     * 只有补税 / 转人工查验才置重点，且只能由“明确降风险 / 解除重点名单”解除。
     */
    @Transactional
    public BrandPriceRule applyReviewOutcome(String brand, String hsCode, String category,
                                             String decision, BigDecimal confirmedUnitPrice, Long merchantId) {
        boolean suspect = "SUPPLEMENT_TAX".equals(decision) || "MANUAL_INSPECTION".equals(decision);
        // PASS：仅滚动成交价均价，保留既有风险标记不动；存疑结论：以认定价滚动均价
        BrandPriceRule rule = recordDeal(brand, hsCode, category, confirmedUnitPrice);
        if (rule == null) return null;
        rule.setLastReviewDecision(decision);
        rule.setLastMerchantId(merchantId);
        if (suspect) {
            // 只有补税/转人工才能置重点；PASS 不允许把已有重点标记改回 false
            rule.setReviewFlag(true);
        }
        rule.setUpdatedAt(LocalDateTime.now());
        ruleRepository.save(rule);
        if (suspect) {
            upsertWatch(merchantId, brand, hsCode, true, decision);
        }
        // PASS 分支刻意不调用 upsertWatch(...,false,...)：不得解除既有重点复核处置
        return rule;
    }

    /**
     * 明确降风险（HIGH 调低）时解除该商家全部品牌品类的重点复核名单，恢复普通预审。
     */
    @Transactional
    public int releaseMerchantWatches(Long merchantId) {
        var list = watchRepository.findByMerchantIdOrderByUpdatedAtDesc(merchantId);
        int n = 0;
        for (MerchantPriceWatch w : list) {
            if (Boolean.TRUE.equals(w.getStricterReview())) {
                w.setStricterReview(false);
                w.setLastDecision("RELEASED_BY_RISK_DOWNGRADE");
                w.setUpdatedAt(LocalDateTime.now());
                watchRepository.save(w);
                n++;
            }
        }
        return n;
    }

    /** 显式解除某一条商家×品牌品类重点复核记录（管理员/海关） */
    @Transactional
    public MerchantPriceWatch releaseWatch(Long watchId) {
        MerchantPriceWatch w = watchRepository.findById(watchId)
                .orElseThrow(() -> new com.port.inspection.exception.BizException(
                        "重点复核记录不存在", org.springframework.http.HttpStatus.NOT_FOUND));
        w.setStricterReview(false);
        w.setLastDecision("RELEASED_MANUALLY");
        w.setUpdatedAt(LocalDateTime.now());
        return watchRepository.save(w);
    }

    /** 全部重点复核名单（管理端） */
    public java.util.List<MerchantPriceWatch> listAllWatches() {
        return watchRepository.findAll();
    }

    public MerchantPriceWatch getWatch(Long id) {
        return watchRepository.findById(id).orElse(null);
    }

    @Transactional
    public void upsertWatch(Long merchantId, String brand, String hsCode, boolean stricter, String decision) {
        if (merchantId == null || brand == null || brand.isBlank()) return;
        MerchantPriceWatch w = watchRepository
                .findByMerchantIdAndBrandAndHsCode(merchantId, brand.trim(), hsCode)
                .orElseGet(() -> {
                    MerchantPriceWatch nw = new MerchantPriceWatch();
                    nw.setMerchantId(merchantId);
                    nw.setBrand(brand.trim());
                    nw.setHsCode(hsCode);
                    return nw;
                });
        w.setStricterReview(stricter);
        w.setLastDecision(decision);
        w.setUpdatedAt(LocalDateTime.now());
        watchRepository.save(w);
    }
}
