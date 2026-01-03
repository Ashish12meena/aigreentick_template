package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "blacklists")
@Data
public class Blacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "mobile", nullable = false, length = 255)
    private String mobile;

    @Column(name = "country_id", nullable = false)
    private Long countryId;

    /**
     * enum('0','1')
     * 0 = not blocked
     * 1 = blocked
     */
    @Column(name = "is_blocked", nullable = false, columnDefinition = "enum('0','1')")
    private String isBlocked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
