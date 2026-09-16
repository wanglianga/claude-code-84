package com.port.inspection.repository;

import com.port.inspection.model.ReturnOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ReturnOrderRepository extends JpaRepository<ReturnOrder, Long> {
    List<ReturnOrder> findByParcelId(Long parcelId);
    Optional<ReturnOrder> findByReturnNo(String returnNo);
    List<ReturnOrder> findByStatus(com.port.inspection.model.enums.ReturnStatus status);
    List<ReturnOrder> findAllByOrderByCreatedAtDesc();
}
