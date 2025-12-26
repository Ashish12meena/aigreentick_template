package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "template_carousel_card_buttons")
@Data
public class TemplateCarouselCardButton {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id")
    private Long cardId;

    @Column(name = "card_button_index")
    private Integer cardButtonIndex; //new 

    private String type; // // quick_reply, url, phone_number

    private String text;

    private String url;

    @Column(name = "phone_number")
    private String phoneNumber;

    private String parameters;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}