package com.port.inspection.repository;

import com.port.inspection.model.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    Optional<Batch> findByBatchNo(String batchNo);
    List<Batch> findAllByOrderByCreatedAtDesc();
}
