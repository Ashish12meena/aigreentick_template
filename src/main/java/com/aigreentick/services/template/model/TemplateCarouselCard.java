package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "template_carousel_cards")
@Data
public class TemplateCarouselCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "component_id")
    private Long componentId;

    private String header;

    private String body;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "media_type")
    private String mediaType;

    @Column(name = "card_index")
    private Integer cardIndex;

    private String parameters;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}