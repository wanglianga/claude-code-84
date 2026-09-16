package com.port.inspection.model;

import com.port.inspection.model.enums.CheckType;
import com.port.inspection.model.enums.PrecheckLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 申报前检查结果（禁限售/价格异常/身份证重复/税费/收件人频次/商家风险） */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "precheck_results")
public class PrecheckResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parcelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PrecheckLevel level;

    @Column(length = 512)
    private String message;

    private LocalDateTime createdAt = LocalDateTime.now();
}
