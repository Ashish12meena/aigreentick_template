package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private String name;

    @Column(name = "previous_category")
    private String previousCategory;

    private String language;

    private String status;

    private String category;

    @Column(name = "wa_id")
    private String waId;

    @Column(columnDefinition = "TEXT")
    private String payload;

    // Store JSON text or map to JsonNode if needed
    @Column(columnDefinition = "JSON")
    private String response;

    // ==================== RELATIONSHIPS ====================

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<TemplateComponent> components = new ArrayList<>();

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    private List<TemplateText> texts = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "template_type")
    private String templateType;

    // ==================== HELPER METHODS ====================

    public void addComponent(TemplateComponent component) {
        components.add(component);
        component.setTemplate(this);
    }

    public void removeComponent(TemplateComponent component) {
        components.remove(component);
        component.setTemplate(null);
    }

    public void addText(TemplateText text) {
        texts.add(text);
        text.setTemplate(this);
    }

    public void removeText(TemplateText text) {
        texts.remove(text);
        text.setTemplate(null);
    }

}
