package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.model.ReturnOrder;
import com.port.inspection.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class ReturnController {

    private final ReturnService returnService;

    /** 发起退运/销毁申请 */
    @PostMapping
    @PreAuthorize("hasAnyRole('MERCHANT','CS','CUSTOMS')")
    public ReturnOrder request(@RequestParam Long parcelId, @Valid @RequestBody Dtos.ReturnApplyRequest req) {
        return returnService.request(parcelId, req, AuthUtils.currentUser());
    }

    @GetMapping
    public List<ReturnOrder> list(@RequestParam(required = false) String status) {
        return returnService.list(status);
    }

    /** 海关核准 */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('CUSTOMS')")
    public ReturnOrder approve(@PathVariable Long id) {
        return returnService.approve(id, AuthUtils.currentUser());
    }

    /** 海关驳回 */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('CUSTOMS')")
    public ReturnOrder reject(@PathVariable Long id) {
        return returnService.reject(id, AuthUtils.currentUser());
    }

    /** 仓库执行退运/销毁 */
    @PostMapping("/{id}/execute")
    @PreAuthorize("hasRole('WAREHOUSE')")
    public ReturnOrder execute(@PathVariable Long id) {
        return returnService.execute(id, AuthUtils.currentUser());
    }
}
