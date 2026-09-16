package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.model.Material;
import com.port.inspection.model.PriceReviewOrder;
import com.port.inspection.model.User;
import com.port.inspection.service.PriceReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 申报价格异常复核：商家传三证，报关员复核（继续申报/补税/转人工查验） */
@RestController
@RequestMapping("/api/price-reviews")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class PriceReviewController {

    private final PriceReviewService priceReviewService;

    /** 复核单列表（商家仅见本商家） */
    @GetMapping
    public List<PriceReviewOrder> list(@RequestParam(required = false) String status) {
        return priceReviewService.list(status, AuthUtils.currentUser());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        PriceReviewOrder o = priceReviewService.getOrder(id);
        User u = AuthUtils.currentUser();
        if (u.getRole() == com.port.inspection.model.enums.Role.MERCHANT
                && !java.util.Objects.equals(o.getMerchantId(), u.getMerchantId())) {
            throw com.port.inspection.exception.BizException.forbidden("只能查看本商家的复核单");
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("review", o);
        v.put("evidence", priceReviewService.evidenceMaterials(o.getParcelId()));
        v.put("requiredEvidence", List.of("PURCHASE_PROOF", "PROMO_EXPLANATION", "PAYMENT_RECORD"));
        return v;
    }

    /** 商家上传采购凭证/促销说明/付款记录；三证齐备自动转待复核 */
    @PostMapping("/{id}/evidence")
    @PreAuthorize("hasAnyRole('MERCHANT','BROKER','CS','ADMIN')")
    public Material uploadEvidence(@PathVariable Long id, @Valid @RequestBody Dtos.MaterialUploadRequest req) {
        return priceReviewService.uploadEvidence(id, req, AuthUtils.currentUser());
    }

    /** 报关员复核结论：PASS 继续申报 / SUPPLEMENT_TAX 补税 / MANUAL_INSPECTION 转人工查验 */
    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('BROKER','CUSTOMS','ADMIN')")
    public PriceReviewOrder decide(@PathVariable Long id, @Valid @RequestBody Dtos.PriceReviewDecisionRequest req) {
        return priceReviewService.decide(id, req, AuthUtils.currentUser());
    }
}
