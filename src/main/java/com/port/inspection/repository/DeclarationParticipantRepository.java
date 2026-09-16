package com.port.inspection.repository;

import com.port.inspection.model.DeclarationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface DeclarationParticipantRepository extends JpaRepository<DeclarationParticipant, Long> {
    List<DeclarationParticipant> findByDeclarationId(Long declarationId);
}
