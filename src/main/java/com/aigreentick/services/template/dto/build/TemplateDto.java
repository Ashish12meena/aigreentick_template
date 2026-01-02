package com.aigreentick.services.template.dto.build;

import java.util.List;


import lombok.Data;

@Data
public class TemplateDto {
    private String id;
    private String name;
    private String category;
    private String language;
    private String status;
    private String metaTemplateId;
    private List<TemplateComponentDto> components;
    private List<TemplateTextDto> texts;
}
