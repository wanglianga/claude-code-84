package com.port.inspection.model;

import com.port.inspection.model.enums.FailAction;
import com.port.inspection.model.enums.FailReason;
import com.port.inspection.model.enums.InspectionOrderStatus;
import com.port.inspection.model.enums.InspectionVerdict;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 海关查验指令 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inspection_orders")
public class InspectionOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String orderNo;

    @Column(nullable = false)
    private Long declarationId;

    @Column(nullable = false)
    private Long parcelId;

    /** 查验指令内容 */
    @Column(nullable = false, length = 255)
    private String instruction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InspectionOrderStatus status = InspectionOrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InspectionVerdict verdict;

    @Enumerated(EnumType.STRING)
    @Column(length = 64)
    private FailReason failReason;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FailAction failAction;

    @Column(length = 64)
    private String issuedBy;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime completedAt;
}
