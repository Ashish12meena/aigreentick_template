package com.aigreentick.services.template.dto.request.template.create;

import com.aigreentick.services.template.dto.request.template.TemplateRequest;

import lombok.Data;

@Data
public class CreateTemplateRequestDto {
    // This goes to Facebook
    private TemplateRequest template;

    private VariableDefaultsDto variableDefaults;
}
