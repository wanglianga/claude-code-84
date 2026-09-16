package com.port.inspection.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 商家 × 品牌品类 重点复核名单：补税/转人工后纳入，后续同类商品进入更严格预审 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "merchant_price_watches")
public class MerchantPriceWatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long merchantId;

    @Column(nullable = false, length = 64)
    private String brand;

    @Column(nullable = false, length = 20)
    private String hsCode;

    @Column(nullable = false)
    private Boolean stricterReview = true;

    @Column(length = 20)
    private String lastDecision;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
