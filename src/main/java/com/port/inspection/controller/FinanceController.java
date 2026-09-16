package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.model.Compensation;
import com.port.inspection.model.TaxRecord;
import com.port.inspection.service.FinanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/taxes")
    public List<TaxRecord> taxes(@RequestParam(required = false) String status) {
        return financeService.listTaxes(status);
    }

    /** 财务代缴税费（缴清且海关审结 → 自动放行） */
    @PostMapping("/taxes/{id}/pay")
    @PreAuthorize("hasRole('FINANCE')")
    public TaxRecord payTax(@PathVariable Long id) {
        return financeService.payTax(id, AuthUtils.currentUser());
    }

    @GetMapping("/compensations")
    public List<Compensation> compensations(@RequestParam(required = false) String status) {
        return financeService.listCompensations(status);
    }

    /** 登记赔付（客服/仓库/财务） */
    @PostMapping("/compensations")
    @PreAuthorize("hasAnyRole('CS','WAREHOUSE','FINANCE')")
    public Compensation createCompensation(@Valid @RequestBody Dtos.CompensationCreateRequest req) {
        return financeService.createCompensation(req, AuthUtils.currentUser());
    }

    /** 财务审批赔付 */
    @PostMapping("/compensations/{id}/approve")
    @PreAuthorize("hasRole('FINANCE')")
    public Compensation approve(@PathVariable Long id) {
        return financeService.approve(id, AuthUtils.currentUser());
    }

    /** 财务支付赔付 */
    @PostMapping("/compensations/{id}/pay")
    @PreAuthorize("hasRole('FINANCE')")
    public Compensation pay(@PathVariable Long id) {
        return financeService.pay(id, AuthUtils.currentUser());
    }
}
