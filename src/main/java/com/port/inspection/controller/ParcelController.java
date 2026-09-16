package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.model.Parcel;
import com.port.inspection.model.PrecheckResult;
import com.port.inspection.model.User;
import com.port.inspection.service.ParcelService;
import com.port.inspection.service.PrecheckService;
import com.port.inspection.service.DeclarationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/packages")
@RequiredArgsConstructor
public class ParcelController {

    private final ParcelService parcelService;
    private final PrecheckService precheckService;
    private final DeclarationService declarationService;

    /** 商家入仓登记 */
    @PostMapping
    @PreAuthorize("hasRole('MERCHANT')")
    public Parcel create(@Valid @RequestBody Dtos.ParcelCreateRequest req) {
        return parcelService.createParcel(req, AuthUtils.currentUser());
    }

    /** 多平台订单合包 */
    @PostMapping("/consolidate")
    @PreAuthorize("hasRole('MERCHANT')")
    public Parcel consolidate(@Valid @RequestBody Dtos.ConsolidateRequest req) {
        return parcelService.consolidate(req, AuthUtils.currentUser());
    }

    @GetMapping
    public List<Parcel> list(@RequestParam(required = false) String status) {
        return parcelService.listForUser(AuthUtils.currentUser(), status);
    }

    @GetMapping("/{id}")
    public Parcel detail(@PathVariable Long id) {
        return parcelService.getAndCheckOwner(id, AuthUtils.currentUser());
    }

    /** 包裹档案：每个节点的材料、税费、时效、责任和赔付（内部全量视图） */
    @GetMapping("/{id}/archive")
    public Map<String, Object> archive(@PathVariable Long id) {
        return parcelService.archive(id, AuthUtils.currentUser());
    }

    /** 申报前检查 */
    @PostMapping("/{id}/precheck")
    @PreAuthorize("hasAnyRole('MERCHANT','WAREHOUSE','BROKER','ADMIN')")
    public List<PrecheckResult> precheck(@PathVariable Long id) {
        return precheckService.runPrecheck(id, AuthUtils.currentUser());
    }

    /** 包裹级材料上传（高风险商家申报前传票） */
    @PostMapping("/{id}/materials")
    @PreAuthorize("hasAnyRole('MERCHANT','WAREHOUSE','BROKER')")
    public com.port.inspection.model.Material uploadParcelMaterial(@PathVariable Long id,
            @Valid @RequestBody Dtos.MaterialUploadRequest req) {
        return declarationService.uploadParcelMaterial(id, req, AuthUtils.currentUser());
    }

    /** 保税仓转一般贸易 */
    @PostMapping("/{id}/convert-trade-mode")
    @PreAuthorize("hasAnyRole('MERCHANT','BROKER')")
    public Parcel convertTradeMode(@PathVariable Long id) {
        return parcelService.convertTradeMode(id, AuthUtils.currentUser());
    }

    /** 放行后安排国内派送 */
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasRole('WAREHOUSE')")
    public Parcel dispatch(@PathVariable Long id) {
        return parcelService.dispatch(id, AuthUtils.currentUser());
    }

    /** 签收 */
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasRole('WAREHOUSE')")
    public Parcel deliver(@PathVariable Long id) {
        return parcelService.deliver(id, AuthUtils.currentUser());
    }
}
