package com.aigreentick.services.template.dto.request.template;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class TemplateComponentCardsRequest {
     private Integer index;
    private List<TemplateCarouselCardComponentRequest> components;
}
