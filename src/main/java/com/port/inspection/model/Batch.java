package com.port.inspection.model;

import com.port.inspection.model.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 口岸仓每日批次：批量处理包裹，异常包裹与正常放行队列分离 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "batches")
public class Batch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String batchNo;

    @Column(nullable = false)
    private Long merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BatchStatus status = BatchStatus.OPEN;

    @Column(nullable = false)
    private Integer totalCount = 0;

    /** 正常放行队列数量 */
    @Column(nullable = false)
    private Integer normalCount = 0;

    /** 异常隔离队列数量 */
    @Column(nullable = false)
    private Integer abnormalCount = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime processedAt;
}
