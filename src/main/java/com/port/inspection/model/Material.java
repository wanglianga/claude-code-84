package com.port.inspection.model;

import com.port.inspection.model.enums.MaterialType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 包裹/申报单材料：发票、认证、开箱照片、情况说明等 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "materials")
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parcelId;

    private Long declarationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MaterialType materialType;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 512)
    private String fileUrl;

    @Column(length = 64)
    private String uploadedBy;

    private LocalDateTime createdAt = LocalDateTime.now();
}
