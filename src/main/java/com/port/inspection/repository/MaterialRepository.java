package com.port.inspection.repository;

import com.port.inspection.model.Material;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface MaterialRepository extends JpaRepository<Material, Long> {
    List<Material> findByParcelId(Long parcelId);
    List<Material> findByDeclarationId(Long declarationId);
    List<Material> findByParcelIdAndMaterialType(Long parcelId, com.port.inspection.model.enums.MaterialType type);
}
