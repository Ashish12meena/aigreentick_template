package com.aigreentick.services.template.dto.request;

import lombok.Data;

@Data
public class TemplateTextDto {
    private Integer variableIndex;
    private String text;
    private String type;
}
