package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "template_texts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    @JsonBackReference
    private Template template;

    @Column(name = "type")
    private String type; // new
    
    @Column(name = "text")
    private String text;

    @Column(name = "is_carousel")
    private Boolean isCarousel;
    
    @Column(name = "card_index")
    private Integer cardIndex;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "text_index")
    private Integer textIndex;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

}
