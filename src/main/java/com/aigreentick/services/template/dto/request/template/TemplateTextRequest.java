package com.aigreentick.services.template.dto.request.template;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class TemplateTextRequest {
    private String type;
    private boolean isCarousel;
    private Integer cardIndex;
    private Integer textIndex;
    private String text;
}
