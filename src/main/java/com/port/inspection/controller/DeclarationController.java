package com.port.inspection.controller;

import com.port.inspection.config.AuthUtils;
import com.port.inspection.dto.Dtos;
import com.port.inspection.model.Declaration;
import com.port.inspection.model.Material;
import com.port.inspection.service.DeclarationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/declarations")
@RequiredArgsConstructor
@PreAuthorize("!hasRole('CONSUMER')")
public class DeclarationController {

    private final DeclarationService declarationService;

    /** 创建申报单（六方协同单） */
    @PostMapping
    @PreAuthorize("hasAnyRole('MERCHANT','BROKER')")
    public Declaration create(@RequestParam Long parcelId) {
        return declarationService.createDeclaration(parcelId, AuthUtils.currentUser());
    }

    @GetMapping
    public List<Declaration> list() {
        return declarationService.listForUser(AuthUtils.currentUser());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return declarationService.detail(id);
    }

    /** 提交海关（含补材料后重新提交） */
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('MERCHANT','BROKER')")
    public Declaration submit(@PathVariable Long id) {
        return declarationService.submit(id, AuthUtils.currentUser());
    }

    /** 上传材料（发票/认证/照片/说明） */
    @PostMapping("/{id}/materials")
    @PreAuthorize("hasAnyRole('MERCHANT','WAREHOUSE','BROKER')")
    public Material uploadMaterial(@PathVariable Long id, @Valid @RequestBody Dtos.MaterialUploadRequest req) {
        return declarationService.uploadMaterial(id, req, AuthUtils.currentUser());
    }

    /** 商家拒绝补材料 → 包裹扣留、商家违规 +1 */
    @PostMapping("/{id}/refuse-supplement")
    @PreAuthorize("hasRole('MERCHANT')")
    public Declaration refuseSupplement(@PathVariable Long id) {
        return declarationService.refuseSupplement(id, AuthUtils.currentUser());
    }
}
