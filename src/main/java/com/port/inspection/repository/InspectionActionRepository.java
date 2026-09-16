package com.port.inspection.repository;

import com.port.inspection.model.InspectionAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface InspectionActionRepository extends JpaRepository<InspectionAction, Long> {
    List<InspectionAction> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}
