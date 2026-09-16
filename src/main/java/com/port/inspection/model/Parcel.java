package com.port.inspection.model;

import com.port.inspection.model.enums.PackageStatus;
import com.port.inspection.model.enums.PackageType;
import com.port.inspection.model.enums.TradeMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 跨境包裹（运单维度） */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "packages")
public class Parcel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 运单号 */
    @Column(nullable = false, unique = true, length = 64)
    private String waybillNo;

    @Column(nullable = false)
    private Long merchantId;

    /** 商品编码（HS Code） */
    @Column(nullable = false, length = 20)
    private String hsCode;

    /** 品牌（用于同品牌同类商品价格异常复核） */
    @Column(length = 64)
    private String brand;

    @Column(nullable = false, length = 128)
    private String goodsName;

    /** 申报价格（单价） */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal declaredPrice;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(nullable = false, length = 64)
    private String recipientName;

    /** 收件人身份证 */
    @Column(nullable = false, length = 32)
    private String recipientIdCard;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    /** 批次号 */
    @Column(length = 64)
    private String batchNo;

    /** 仓位 */
    @Column(length = 32)
    private String warehouseLocation;

    /** 物流渠道 */
    @Column(nullable = false, length = 32)
    private String logisticsChannel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PackageType packageType = PackageType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TradeMode tradeMode = TradeMode.BONDED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PackageStatus status = PackageStatus.RECEIVED;

    /** 海关系统延迟标记 */
    @Column(nullable = false)
    private Boolean customsDelayed = false;

    /** 计税单价：默认等于申报价；价格复核补税时取认定成交价（影响税费） */
    @Column(precision = 12, scale = 2)
    private BigDecimal taxablePrice;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
