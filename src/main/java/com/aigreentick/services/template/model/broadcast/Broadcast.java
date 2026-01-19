package com.aigreentick.services.template.model.broadcast;
    
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Entity
@Table(name = "broadcasts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Broadcast {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ================= BASIC ================= */

    @Column(name = "source", length = 255)
    @Builder.Default
    private String source = "WEB";

    /* ================= RELATION IDS ================= */

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "wallet_id")
    private Integer walletId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "whatsapp")
    private Integer whatsapp;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    /* ================= CAMPAIGN INFO ================= */

    @Column(name = "campname", nullable = false, length = 255)
    private String campname;

    @Column(name = "is_media", nullable = false, columnDefinition = "enum('0','1')")
    private String isMedia;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data")
    private Map<String, Object> data;

    @Column(name = "total", nullable = false)
    @Builder.Default
    private Integer total = 0;

    @Column(name = "schedule_at")
    private LocalDateTime scheduleAt;

    @Column(name = "status", nullable = false,columnDefinition = "enum('0','1','2')")
    @Builder.Default
    private String status = "1";

    /* ================= PAYLOAD ================= */

    @Lob
    @Column(name = "numbers", columnDefinition = "LONGTEXT")
    private String numbers;

    @Lob
    @Column(name = "requests", columnDefinition = "LONGTEXT")
    private String requests;

    /* ================= TIMESTAMPS ================= */

    @Column(name = "created_at", nullable = false, updatable = false)
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

    /* ================= ENUMS (PIN-TO-PIN) ================= */

    public enum IsMedia {
        _0, _1
    }

    public enum Status {
        _0, _1, _2
    }
}