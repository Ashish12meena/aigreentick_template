package com.aigreentick.services.template.dto.build;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateComponentDto {
    private String type;
    private String format;
    private String text;
    private String imageUrl;
    private String mediaUrl;
    private Boolean addSecurityRecommendation;
    private Integer codeExpirationMinutes;
    private List<TemplateComponentButtonDto> buttons;
    private List<TemplateComponentCardsDto> cards;
    private TemplateExampleDto example;
}
