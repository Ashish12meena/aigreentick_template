package com.aigreentick.services.template.dto.request;

import java.util.List;

import lombok.Data;

@Data
public class CreateTemplateResponseDto {
    private TemplateRequest template;
    private List<TemplateTextRequest> variables;
}
