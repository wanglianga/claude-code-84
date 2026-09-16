package com.port.inspection.repository;

import com.port.inspection.model.ParcelEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ParcelEventRepository extends JpaRepository<ParcelEvent, Long> {
    List<ParcelEvent> findByParcelIdOrderByCreatedAtAsc(Long parcelId);
}
