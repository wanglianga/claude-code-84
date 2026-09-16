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
     * 复核结论沉淀到品牌规则：
     * 继续申报 → 以申报价刷新均价、解除重点标记；补税 → 以认定价刷新均价并置重点；转人工 → 置重点。
     */
    @Transactional
    public BrandPriceRule applyReviewOutcome(String brand, String hsCode, String category,
                                             String decision, BigDecimal confirmedUnitPrice, Long merchantId) {
        BrandPriceRule rule = recordDeal(brand, hsCode, category, confirmedUnitPrice);
        if (rule == null) return null;
        boolean suspect = "SUPPLEMENT_TAX".equals(decision) || "MANUAL_INSPECTION".equals(decision);
        rule.setReviewFlag(suspect);
        rule.setLastReviewDecision(decision);
        rule.setLastMerchantId(merchantId);
        rule.setUpdatedAt(LocalDateTime.now());
        ruleRepository.save(rule);
        upsertWatch(merchantId, brand, hsCode, suspect, decision);
        return rule;
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
