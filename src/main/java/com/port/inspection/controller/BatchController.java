package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.model.Batch;
import com.port.inspection.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class BatchController {

    private final BatchService batchService;

    /** 创建批次（商家） */
    @PostMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public Batch create(@Valid @RequestBody Dtos.BatchCreateRequest req) {
        return batchService.create(req.batchNo(), AuthUtils.currentUser());
    }

    @GetMapping
    public List<Batch> list() {
        return batchService.list();
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return batchService.detail(id);
    }

    /** 批量处理：正常包裹进放行队列，异常包裹隔离 */
    @PostMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('MERCHANT','WAREHOUSE')")
    public Map<String, Object> process(@PathVariable Long id) {
        return batchService.process(id, AuthUtils.currentUser());
    }
}
