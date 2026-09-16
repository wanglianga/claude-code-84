package com.port.inspection.model;

import com.port.inspection.model.enums.DeclarationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 申报单：商家、仓库、报关员、客服、海关接口、财务在同一单中协同处理 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "declarations")
public class Declaration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String declarationNo;

    @Column(nullable = false)
    private Long parcelId;

    @Column(nullable = false)
    private Long merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeclarationStatus status = DeclarationStatus.DRAFT;

    /** 预估/应缴税费 */
    @Column(precision = 12, scale = 2)
    private BigDecimal taxAmount;

    /** 查验不通过原因 */
    @Column(length = 64)
    private String failReason;

    /** 补材料要求说明 */
    @Column(length = 512)
    private String supplementNote;

    @Column(length = 64)
    private String submittedBy;

    private LocalDateTime submittedAt;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
