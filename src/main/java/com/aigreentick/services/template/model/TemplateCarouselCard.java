package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

@Entity
@Table(name = "template_carousel_cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateCarouselCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id")
    private Long templateId;

     @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    @JsonBackReference
    private TemplateComponent component;

    private String header;

    private String body;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "media_type")
    private String mediaType;

    @Column(name = "card_index")
    private Integer cardIndex;

    private String parameters;

       // ==================== RELATIONSHIPS ====================

    @OneToMany(mappedBy = "card", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference 
    private List<TemplateCarouselCardButton> buttons = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ==================== HELPER METHODS ====================

    public void addButton(TemplateCarouselCardButton button) {
        buttons.add(button);
        button.setCard(this);
    }

    public void removeButton(TemplateCarouselCardButton button) {
        buttons.remove(button);
        button.setCard(null);
    }

}