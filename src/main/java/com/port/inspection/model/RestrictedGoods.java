package com.port.inspection.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 禁限售商品规则 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "restricted_goods")
public class RestrictedGoods {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 命中的 HS 编码前缀（可空） */
    @Column(length = 20)
    private String hsCode;

    /** 命中的商品名关键词（可空） */
    @Column(length = 64)
    private String keyword;

    /** PROHIBITED 禁止进口 / RESTRICTED 限制进口（需证明） */
    @Column(nullable = false, length = 20)
    private String ruleType;

    @Column(length = 255)
    private String description;
}
