package com.aigreentick.services.template.model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    /* ================= PRIMARY KEY ================= */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ================= USER INFO ================= */

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /* ================= AMOUNT ================= */

    @Column(name = "amount", nullable = false)
    private Double amount;

    /* ================= ENUMS ================= */

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, columnDefinition = "enum('credit','debit')")
    private WalletType type;


    // you can't enumify this
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "enum('1','0','2') COMMENT '1 is active | 0 is deactive | 2 is ban'"
    )
    private String status;

    /* ================= META ================= */

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "transection", nullable = false, length = 255)
    private String transection;

    /* ================= REFERENCES ================= */

    @Column(name = "broadcast_id")
    private Integer broadcastId;

    @Column(name = "scheduled_id")
    private Integer scheduledId;

    /* ================= TIMESTAMPS ================= */

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public enum WalletType {
        credit,
        debit
    }
}