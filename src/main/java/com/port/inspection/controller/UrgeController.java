package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.model.ConsumerUrge;
import com.port.inspection.service.UrgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/urges")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class UrgeController {

    private final UrgeService urgeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CS','ADMIN','WAREHOUSE')")
    public List<ConsumerUrge> list(@RequestParam(required = false) String status) {
        return urgeService.list(status);
    }

    /** 客服处理催件 */
    @PostMapping("/{id}/handle")
    @PreAuthorize("hasRole('CS')")
    public ConsumerUrge handle(@PathVariable Long id, @Valid @RequestBody Dtos.HandleUrgeRequest req) {
        return urgeService.handle(id, req.note(), AuthUtils.currentUser());
    }
}
