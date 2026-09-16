package com.port.inspection.model;

import com.port.inspection.model.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 商家及其风控参数 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "merchants")
public class Merchant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskLevel riskLevel = RiskLevel.LOW;

    /** 海关抽检比例（%），高风险商家可调高 */
    @Column(nullable = false)
    private Integer inspectionRatio = 10;

    /** 单批次最大包裹数，高风险商家限制批量申报 */
    @Column(nullable = false)
    private Integer batchLimit = 100;

    /** 是否要求申报前提前上传完整票据 */
    @Column(nullable = false)
    private Boolean requireAdvanceDocs = false;

    @Column(nullable = false)
    private Integer violationCount = 0;

    @Column(nullable = false)
    private Integer totalDeclarations = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
}
