package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.model.InspectionAction;
import com.port.inspection.model.InspectionOrder;
import com.port.inspection.service.InspectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inspections")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class InspectionController {

    private final InspectionService inspectionService;

    @GetMapping("/orders")
    public List<InspectionOrder> orders(@RequestParam(required = false) String status) {
        return inspectionService.listOrders(status);
    }

    @GetMapping("/orders/{id}/actions")
    public List<InspectionAction> actions(@PathVariable Long id) {
        return inspectionService.listActions(id);
    }

    /** 仓库人员执行查验动作：开箱拍照/核对商品/补充票据/提交说明 */
    @PostMapping("/orders/{id}/actions")
    @PreAuthorize("hasRole('WAREHOUSE')")
    public InspectionAction execute(@PathVariable Long id, @Valid @RequestBody Dtos.InspectionActionRequest req) {
        return inspectionService.executeAction(id, req, AuthUtils.currentUser());
    }

    /** 海关提交查验结论：通过 → 放行；不通过 → 补材料/扣留/退运/销毁 */
    @PostMapping("/orders/{id}/result")
    @PreAuthorize("hasAnyRole('CUSTOMS','BROKER')")
    public InspectionOrder result(@PathVariable Long id, @Valid @RequestBody Dtos.InspectionResultRequest req) {
        return inspectionService.submitResult(id, req, AuthUtils.currentUser());
    }
}
