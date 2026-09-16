package com.port.inspection.model;

import com.port.inspection.model.enums.CompensationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 赔付记录：包裹破损/销毁/丢失等责任赔付 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "compensations")
public class Compensation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parcelId;

    private Long declarationId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 255)
    private String reason;

    /** 责任方：MERCHANT 商家 / WAREHOUSE 仓库 / LOGISTICS 物流 / PLATFORM 平台 */
    @Column(nullable = false, length = 20)
    private String responsibleParty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompensationStatus status = CompensationStatus.PENDING;

    @Column(length = 64)
    private String createdBy;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime paidAt;
}
