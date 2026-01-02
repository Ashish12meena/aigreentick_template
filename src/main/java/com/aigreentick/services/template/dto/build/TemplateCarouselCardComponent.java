package com.aigreentick.services.template.dto.build;

import java.util.List;

import lombok.Data;

@Data
public class TemplateCarouselCardComponent {
       private String type;
     private String format; // IMAGE, VIDEO, DOCUMENT
    private TemplateCarouselExample example;
    private String text;
    private List<TemplateCarouselButton> buttons;
}
