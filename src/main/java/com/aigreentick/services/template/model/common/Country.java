package com.aigreentick.services.template.model.common;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "countries")
@Data
public class Country {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "mobile_code", nullable = false, length = 255)
    private String mobileCode;

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
