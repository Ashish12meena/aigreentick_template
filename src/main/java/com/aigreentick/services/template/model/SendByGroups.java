package com.aigreentick.services.template.model;


import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "send_by_groups")
public class SendByGroups {

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
}
