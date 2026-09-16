package com.port.inspection.model;

import com.port.inspection.model.enums.ReturnStatus;
import com.port.inspection.model.enums.ReturnType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 退运/销毁处置单 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "return_orders")
public class ReturnOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String returnNo;

    @Column(nullable = false)
    private Long parcelId;

    private Long declarationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnType type;

    @Column(nullable = false, length = 255)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReturnStatus status = ReturnStatus.REQUESTED;

    @Column(length = 64)
    private String requestedBy;

    @Column(length = 64)
    private String approvedBy;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime completedAt;
}
