package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.model.ConsumerUrge;
import com.port.inspection.model.Parcel;
import com.port.inspection.model.User;
import com.port.inspection.service.ParcelService;
import com.port.inspection.service.UrgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 消费者端：只展示必要进度（公开查询 + 登录催件） */
@RestController
@RequiredArgsConstructor
public class ConsumerController {

    private final ParcelService parcelService;
    private final UrgeService urgeService;

    /** 公开物流进度查询（无需登录，只返回必要进度） */
    @GetMapping("/api/public/track/{waybillNo}")
    public Map<String, Object> track(@PathVariable String waybillNo) {
        return parcelService.consumerTrack(waybillNo);
    }

    /** 消费者登录后查看本人包裹 */
    @GetMapping("/api/consumer/packages")
    @PreAuthorize("hasRole('CONSUMER')")
    public List<Parcel> myPackages() {
        return parcelService.listForUser(AuthUtils.currentUser(), null);
    }

    /** 消费者催件 */
    @PostMapping("/api/consumer/packages/{waybillNo}/urge")
    @PreAuthorize("hasRole('CONSUMER')")
    public ConsumerUrge urge(@PathVariable String waybillNo, @Valid @RequestBody Dtos.UrgeRequest req) {
        return urgeService.urge(waybillNo, req.message(), AuthUtils.currentUser());
    }
}
