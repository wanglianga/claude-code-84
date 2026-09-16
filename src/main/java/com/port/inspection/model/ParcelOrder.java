package com.port.inspection.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 多平台订单合包的子订单 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "package_orders")
public class ParcelOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parcelId;

    /** 平台：天猫国际/京东国际/拼多多全球购等 */
    @Column(nullable = false, length = 32)
    private String platform;

    @Column(nullable = false, length = 64)
    private String orderNo;
}
