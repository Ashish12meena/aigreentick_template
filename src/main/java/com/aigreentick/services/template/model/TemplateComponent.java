package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "template_components")
@Data
public class TemplateComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id")
    private Long templateId;

    private String type;

    private String format;

    private String text;

    /**
     * Indicates whether a security recommendation should be added for this
     * component.
     */
    @Column(name = "add_security_recommendation")
    private Boolean addSecurityRecommendation;  // new 

    @Column(name = "code_expiration_minutes")
    private Integer codeExpirationMinutes;  // new

    //now this url act as mediaUrl and imageUrl also
    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

}