package com.aigreentick.services.template.model.account;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "whatsapp_accounts")
@Data
public class WhatsappAccount {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "whatsapp_no", nullable = false, length = 255)
    private String whatsappNo;

    @Column(name = "whatsapp_no_id", nullable = false, length = 255)
    private String whatsappNoId;

    @Column(name = "whatsapp_biz_id", nullable = false, length = 255)
    private String whatsappBizId;

    @Column(name = "parmenent_token", nullable = false, length = 255)
    private String parmenentToken;

    @Column(name = "token", length = 200)
    private String token;

    /**
     * Values:
     * 1 = active
     * 0 = deactive
     * 2 = ban
     */
    @Column(name = "status", nullable = false, columnDefinition = "enum('1','0','2')")
    private String status;

    @Column(name = "response", columnDefinition = "text")
    private String response;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(IST);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(IST);
    }

    @PreRemove
    protected void onDelete() {
        this.deletedAt = LocalDateTime.now(IST);
    }
}
