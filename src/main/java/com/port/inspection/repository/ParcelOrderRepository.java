package com.port.inspection.repository;

import com.port.inspection.model.ParcelOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ParcelOrderRepository extends JpaRepository<ParcelOrder, Long> {
    List<ParcelOrder> findByParcelId(Long parcelId);
}
