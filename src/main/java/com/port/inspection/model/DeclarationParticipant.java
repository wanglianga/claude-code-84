package com.port.inspection.model;

import com.port.inspection.model.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 申报单协同方：商家/仓库/报关员/客服/海关接口/财务 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "declaration_participants",
        uniqueConstraints = @UniqueConstraint(columnNames = {"declarationId", "role"}))
public class DeclarationParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long declarationId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 64)
    private String displayName;
}
