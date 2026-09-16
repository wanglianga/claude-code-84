package com.port.inspection.model;

import com.port.inspection.model.enums.TaxStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 税费记录：跨境电商综合税 / 一般贸易税 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tax_records")
public class TaxRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long declarationId;

    @Column(nullable = false)
    private Long parcelId;

    /** 税种：跨境综合税 / 一般贸易税 */
    @Column(nullable = false, length = 32)
    private String taxType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaxStatus status = TaxStatus.PENDING;

    @Column(length = 64)
    private String paidBy;

    private LocalDateTime paidAt;

    /** 作废原因（status=VOID 时必填：退运/销毁处置完成，税费取消、不可再缴纳） */
    @Column(length = 255)
    private String voidReason;

    private LocalDateTime createdAt = LocalDateTime.now();
}
