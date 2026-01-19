package com.aigreentick.services.template.model.common;


import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@Entity
@Table(name = "send_by_groups")
public class SendByGroups {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "country_id")
    private Long countryId;

    /**
     * DB: enum('0','1')
     */
    @Column(name = "is_media", nullable = false,  columnDefinition = "enum('0','1')")
    private String isMedia;

    @Column(name = "total", nullable = false)
    private Integer total;

    /**
     * DB: enum('0','1')
     */
    @Column(name = "status", nullable = false, columnDefinition = "enum('0','1')")
    private String status;

    @Column(name = "schedule_at")
    private LocalDateTime scheduleAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
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
