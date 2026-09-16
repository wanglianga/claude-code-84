package com.port.inspection.repository;

import com.port.inspection.model.TaxRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface TaxRecordRepository extends JpaRepository<TaxRecord, Long> {
    List<TaxRecord> findByDeclarationId(Long declarationId);
    List<TaxRecord> findByParcelId(Long parcelId);
    List<TaxRecord> findByStatus(com.port.inspection.model.enums.TaxStatus status);
    List<TaxRecord> findAllByOrderByCreatedAtDesc();
}
