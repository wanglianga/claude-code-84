package com.port.inspection.controller;

import com.port.inspection.dto.Dtos;
import com.port.inspection.model.Merchant;
import com.port.inspection.service.MerchantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/merchants")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class MerchantController {

    private final MerchantService merchantService;

    @GetMapping
    public List<Merchant> list() {
        return merchantService.list();
    }

    /** 调整商家风控：提高抽检比例、限制批量申报、要求提前上传票据 */
    @PutMapping("/{id}/risk")
    @PreAuthorize("hasAnyRole('ADMIN','CUSTOMS')")
    public Merchant updateRisk(@PathVariable Long id, @Valid @RequestBody Dtos.MerchantRiskRequest req) {
        return merchantService.updateRisk(id, req);
    }
}
