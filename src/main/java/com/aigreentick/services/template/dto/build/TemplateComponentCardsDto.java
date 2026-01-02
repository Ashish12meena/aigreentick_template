package com.aigreentick.services.template.dto.build;

import java.util.List;

import lombok.Data;

@Data
public class TemplateComponentCardsDto {
  private Integer index;
    private List<TemplateCarouselCardComponent> components;
}
