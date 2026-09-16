package com.port.inspection.model;

import com.port.inspection.model.enums.PriceReviewDecision;
import com.port.inspection.model.enums.PriceReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 申报价格异常复核单：立案 → 三证齐备 → 报关员复核（继续申报/补税/转人工查验） */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "price_review_orders")
public class PriceReviewOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String reviewNo;

    @Column(nullable = false)
    private Long parcelId;

    private Long declarationId;

    @Column(nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 64)
    private String brand;

    @Column(nullable = false, length = 20)
    private String hsCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal declaredPrice;

    /** 立案时参照的历史平均成交价 */
    @Column(precision = 12, scale = 2)
    private BigDecimal referenceAvgPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PriceReviewStatus status = PriceReviewStatus.AWAITING_EVIDENCE;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PriceReviewDecision decision;

    /** 补税结论下认定的计税单价 */
    @Column(precision = 12, scale = 2)
    private BigDecimal revisedUnitPrice;

    @Column(length = 512)
    private String decisionNote;

    @Column(length = 64)
    private String requestedBy;

    @Column(length = 64)
    private String decidedBy;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime decidedAt;
}
