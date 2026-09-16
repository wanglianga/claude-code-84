package com.port.inspection.repository;

import com.port.inspection.model.Declaration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface DeclarationRepository extends JpaRepository<Declaration, Long> {
    Optional<Declaration> findByDeclarationNo(String declarationNo);
    List<Declaration> findByParcelId(Long parcelId);
    List<Declaration> findByMerchantIdOrderByCreatedAtDesc(Long merchantId);
    List<Declaration> findByStatus(com.port.inspection.model.enums.DeclarationStatus status);
    List<Declaration> findAllByOrderByCreatedAtDesc();
}
