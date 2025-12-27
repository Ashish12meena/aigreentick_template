package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "template_texts")
@Data
@Builder
public class TemplateText {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

      @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    @ToString.Exclude
    private Template template;

     private String type; //new 

    private String text;

    private Boolean isCarousel;

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
