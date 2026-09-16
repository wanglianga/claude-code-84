package com.port.inspection.model;

import com.port.inspection.model.enums.PackageStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 包裹档案事件：每个节点的状态流转、责任人与说明（时效/责任留痕） */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "package_events")
public class ParcelEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parcelId;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PackageStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PackageStatus toStatus;

    /** 节点名称，如：入仓登记/申报前检查/海关查验/放行派送 */
    @Column(nullable = false, length = 64)
    private String node;

    /** 操作人（责任留痕） */
    @Column(length = 64)
    private String actor;

    @Column(length = 20)
    private String actorRole;

    @Column(length = 1024)
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();
}
