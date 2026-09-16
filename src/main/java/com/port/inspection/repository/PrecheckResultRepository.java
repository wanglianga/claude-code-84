package com.port.inspection.repository;

import com.port.inspection.model.PrecheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface PrecheckResultRepository extends JpaRepository<PrecheckResult, Long> {
    List<PrecheckResult> findByParcelId(Long parcelId);
    void deleteByParcelId(Long parcelId);
}
