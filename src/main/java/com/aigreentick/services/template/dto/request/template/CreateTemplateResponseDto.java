package com.aigreentick.services.template.dto.request.template;

import java.util.List;

import lombok.Data;

@Data
public class CreateTemplateResponseDto {
    private TemplateRequest template;
    private List<TemplateTextRequest> variables;
}
