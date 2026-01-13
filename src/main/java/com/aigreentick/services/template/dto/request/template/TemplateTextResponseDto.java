package com.aigreentick.services.template.dto.request.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateTextResponseDto {
     /**
     * The ID of the TemplateText entity
     */
    private Long id;

    /**
     * The template ID this variable belongs to
     */
    private Long templateId;

    /**
     * The type of the variable: HEADER, BODY, BUTTON
     */
    private String type;

    /**
     * The variable key/placeholder (e.g., "{{1}}", "{{2}}")
     */
    private String text;

    /**
     * The index of the variable within its type (0-based)
     */
    private Integer textIndex;

    /**
     * The default value for this variable (fallback when attribute not found)
     */
    private String defaultValue;

    /**
     * Whether this is a carousel variable
     */
    private Boolean isCarousel;

    /**
     * For carousel templates - the card index (0-based)
     */
    private Integer cardIndex;
}
