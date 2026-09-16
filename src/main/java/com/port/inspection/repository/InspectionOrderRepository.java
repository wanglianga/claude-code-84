package com.port.inspection.repository;

import com.port.inspection.model.InspectionOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface InspectionOrderRepository extends JpaRepository<InspectionOrder, Long> {
    List<InspectionOrder> findByParcelId(Long parcelId);
    List<InspectionOrder> findByDeclarationId(Long declarationId);
    Optional<InspectionOrder> findByOrderNo(String orderNo);
    List<InspectionOrder> findByStatus(com.port.inspection.model.enums.InspectionOrderStatus status);
    List<InspectionOrder> findAllByOrderByCreatedAtDesc();
}
