package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "template_components")
@Data
@Builder
public class TemplateComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    @ToString.Exclude
    private Template template;


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

     // ==================== RELATIONSHIPS ====================

    @OneToMany(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<TemplateComponentButton> buttons = new ArrayList<>();

    @OneToMany(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    private List<TemplateCarouselCard> carouselCards = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

      // ==================== HELPER METHODS ====================

    public void addButton(TemplateComponentButton button) {
        buttons.add(button);
        button.setComponent(this);
    }

    public void removeButton(TemplateComponentButton button) {
        buttons.remove(button);
        button.setComponent(null);
    }

    public void addCarouselCard(TemplateCarouselCard card) {
        carouselCards.add(card);
        card.setComponent(this);
    }

    public void removeCarouselCard(TemplateCarouselCard card) {
        carouselCards.remove(card);
        card.setComponent(null);
    }

}