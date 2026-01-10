package com.aigreentick.services.template.dto.build;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateTextDto {
    private String type;
    private Integer textIndex;
    private String text;
    private String defaultValue;
    private Boolean isCarousel;
    private Integer cardIndex;
}