package com.port.inspection.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 税则：跨境综合税率、一般贸易税率与参考价（用于价格异常检查） */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tax_rules")
public class TaxRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String hsCode;

    @Column(nullable = false, length = 64)
    private String category;

    /** 跨境电商综合税率 */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    /** 一般贸易综合税率 */
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal generalTaxRate;

    /** 参考价（价格异常判定基准） */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal refPrice;
}
