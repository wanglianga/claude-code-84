package com.port.inspection.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 同品牌同类商品（品牌 + HS 编码）历史成交价规则。
 * 价格复核结论持续沉淀于此：更新参考均价、低报阈值与“重点复核”标记，后续申报提前提示。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "brand_price_rules")
public class BrandPriceRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String brand;

    @Column(nullable = false, length = 20)
    private String hsCode;

    @Column(length = 64)
    private String category;

    /** 历史平均成交价（单价） */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal avgDealPrice;

    @Column(nullable = false)
    private Integer dealCount = 1;

    /** 申报单价低于均价 × 该比例即立案（默认 0.60） */
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal lowReportThreshold = new BigDecimal("0.60");

    /** 低于均价 × 该比例给预警（默认 0.80） */
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal warnThreshold = new BigDecimal("0.80");

    /** 经复核存疑后置真：后续同品牌同类申报更严格预审 */
    @Column(nullable = false)
    private Boolean reviewFlag = false;

    @Column(length = 20)
    private String lastReviewDecision;

    private Long lastMerchantId;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
