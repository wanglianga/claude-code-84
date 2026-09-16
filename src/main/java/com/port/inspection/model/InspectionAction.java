package com.port.inspection.model;

import com.port.inspection.model.enums.InspectionActionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 仓库人员执行的查验动作：开箱拍照/核对商品/补充票据/提交说明 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inspection_actions")
public class InspectionAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InspectionActionType actionType;

    @Column(length = 512)
    private String notes;

    /** 开箱照片地址 */
    @Column(length = 512)
    private String photoUrl;

    @Column(length = 64)
    private String operator;

    private LocalDateTime createdAt = LocalDateTime.now();
}
