package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "send_by_tags")
public class SendByTags {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    /**
     * DB: enum('0','1')
     * Explicit columnDefinition avoids Hibernate 6 validation failure
     */
    @Column(name = "is_media", nullable = false, columnDefinition = "enum('0','1')")
    private String isMedia;

    /**
     * DB: enum('0','1') DEFAULT '0'
     */
    @Column(name = "status", nullable = false, columnDefinition = "enum('0','1')")
    private String status;

    @Column(name = "total", nullable = false)
    private Integer total;

    @Column(name = "schedule_at")
    private LocalDateTime scheduleAt;

    @Column(name = "ex_tag", columnDefinition = "LONGTEXT")
    private String exTag;

    @Column(name = "selected_camp", columnDefinition = "LONGTEXT")
    private String selectedCamp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
