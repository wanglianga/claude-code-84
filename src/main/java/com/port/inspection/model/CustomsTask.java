package com.port.inspection.model;

import com.port.inspection.model.enums.CustomsTaskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 海关系统异步任务（模拟海关审单延迟与回执） */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customs_tasks")
public class CustomsTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long declarationId;

    /** 任务类型：REVIEW 审单 */
    @Column(nullable = false, length = 32)
    private String taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomsTaskStatus status = CustomsTaskStatus.PENDING;

    /** 到该时间后才处理（模拟海关系统延迟） */
    @Column(nullable = false)
    private LocalDateTime executeAfter;

    /** 处理结果：ACCEPTED / INSPECTION_REQUIRED */
    @Column(length = 32)
    private String result;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime processedAt;
}
