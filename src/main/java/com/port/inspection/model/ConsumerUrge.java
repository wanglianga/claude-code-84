package com.port.inspection.model;

import com.port.inspection.model.enums.UrgeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 消费者催件 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "consumer_urges")
public class ConsumerUrge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long parcelId;

    @Column(length = 64)
    private String consumerName;

    @Column(length = 512)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UrgeStatus status = UrgeStatus.OPEN;

    @Column(length = 64)
    private String handledBy;

    @Column(length = 512)
    private String handleNote;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime handledAt;
}
