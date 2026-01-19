package com.aigreentick.services.template.model.template;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "template_carousel_card_buttons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCarouselCardButton {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    @JsonBackReference
    private TemplateCarouselCard card;

    @Column(name = "card_button_index")
    private Integer cardButtonIndex; // new

    @Column(name = "type")
    private String type; // // quick_reply, url, phone_number

    @Column(name = "text")
    private String text;

    @Column(name = "url")
    private String url;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "parameters")
    private String parameters;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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