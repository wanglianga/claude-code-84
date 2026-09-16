package com.port.inspection.repository;

import com.port.inspection.model.CustomsTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface CustomsTaskRepository extends JpaRepository<CustomsTask, Long> {
    List<CustomsTask> findByStatusAndExecuteAfterBefore(com.port.inspection.model.enums.CustomsTaskStatus status, LocalDateTime time);
    List<CustomsTask> findByDeclarationId(Long declarationId);
}
