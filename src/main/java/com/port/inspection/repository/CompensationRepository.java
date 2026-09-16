package com.port.inspection.repository;

import com.port.inspection.model.Compensation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CompensationRepository extends JpaRepository<Compensation, Long> {
    List<Compensation> findByParcelId(Long parcelId);
    List<Compensation> findByStatus(com.port.inspection.model.enums.CompensationStatus status);
    List<Compensation> findAllByOrderByCreatedAtDesc();
}
