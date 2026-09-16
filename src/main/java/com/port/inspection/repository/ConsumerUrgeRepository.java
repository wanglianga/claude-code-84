package com.port.inspection.repository;

import com.port.inspection.model.ConsumerUrge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface ConsumerUrgeRepository extends JpaRepository<ConsumerUrge, Long> {
    List<ConsumerUrge> findByParcelId(Long parcelId);
    List<ConsumerUrge> findByStatus(com.port.inspection.model.enums.UrgeStatus status);
    List<ConsumerUrge> findAllByOrderByCreatedAtDesc();
}
