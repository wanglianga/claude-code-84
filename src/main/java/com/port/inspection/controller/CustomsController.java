package com.port.inspection.controller;

import com.port.inspection.dto.Dtos;
import com.port.inspection.service.CustomsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 海关接口模拟：手动触发回执、模拟海关系统延迟 */
@RestController
@RequestMapping("/api/customs")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class CustomsController {

    private final CustomsService customsService;

    /** 立即处理所有待回执任务（演示/测试） */
    @PostMapping("/process-tasks")
    @PreAuthorize("hasAnyRole('CUSTOMS','ADMIN')")
    public Map<String, Object> processNow() {
        return Map.of("processed", customsService.processNow());
    }

    /** 模拟海关系统延迟：待处理任务全部延后 N 秒 */
    @PostMapping("/simulate-delay")
    @PreAuthorize("hasAnyRole('CUSTOMS','ADMIN')")
    public Map<String, Object> simulateDelay(@Valid @RequestBody Dtos.SimulateDelayRequest req) {
        return Map.of("delayed", customsService.simulateDelay(req.seconds()), "seconds", req.seconds());
    }
}
