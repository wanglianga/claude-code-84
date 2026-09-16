package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.model.BrandPriceRule;
import com.port.inspection.model.MerchantPriceWatch;
import com.port.inspection.service.BrandPriceRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 同品牌同类商品成交价规则（价格复核结论沉淀，后续申报提前提示） */
@RestController
@RequestMapping("/api/brand-price-rules")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class BrandPriceRuleController {

    private final BrandPriceRuleService brandPriceRuleService;

    @GetMapping
    public List<BrandPriceRule> list() {
        return brandPriceRuleService.listRules();
    }

    /** 商家价格重点复核名单（管理端/海关可见全部） */
    @GetMapping("/watches")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMS','BROKER')")
    public List<MerchantPriceWatch> watches() {
        return brandPriceRuleService.listAllWatches();
    }

    /** 当前商家在哪些品牌品类被列入重点复核名单 */
    @GetMapping("/my-watches")
    @PreAuthorize("hasRole('MERCHANT')")
    public Object myWatches() {
        var u = AuthUtils.currentUser();
        return brandPriceRuleService.listWatches(u.getMerchantId());
    }

    /**
     * 明确解除某条商家×品牌品类重点复核记录，恢复普通预审（仅管理员/海关）。
     * 注意：单票价格复核 PASS 不会自动解除；必须由此显式解除或随 HIGH 降风险解除。
     */
    @PostMapping("/watches/{id}/release")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMS')")
    public Map<String, Object> releaseWatch(@PathVariable Long id) {
        MerchantPriceWatch w = brandPriceRuleService.releaseWatch(id);
        return Map.of("id", w.getId(), "stricterReview", w.getStricterReview(),
                "lastDecision", String.valueOf(w.getLastDecision()));
    }
}
