package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.model.BrandPriceRule;
import com.port.inspection.service.BrandPriceRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /** 当前商家在哪些品牌品类被列入重点复核名单 */
    @GetMapping("/my-watches")
    @PreAuthorize("hasRole('MERCHANT')")
    public Object myWatches() {
        var u = AuthUtils.currentUser();
        return brandPriceRuleService.listWatches(u.getMerchantId());
    }
}
