package com.aigreentick.services.template.dto.request;

import java.util.List;

import lombok.Data;

@Data
public class TemplateComponentCardsRequest {
     private Integer index;
    private List<TemplateCarouselCardComponentRequest> components;
}
