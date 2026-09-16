package com.port.inspection.model;

import com.port.inspection.model.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 系统用户（商家/仓库/报关员/客服/海关接口/财务/消费者/管理员） */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 64)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** 商家用户所属商家 ID */
    private Long merchantId;

    /** 消费者身份证号（用于关联本人包裹） */
    @Column(length = 32)
    private String idCard;

    private LocalDateTime createdAt = LocalDateTime.now();
}
