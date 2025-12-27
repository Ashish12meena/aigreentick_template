package com.aigreentick.services.template.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TemplateTextRequest {
    private String type;
    private boolean isCarousel;
    private Integer textIndex;
    private String text;
}
