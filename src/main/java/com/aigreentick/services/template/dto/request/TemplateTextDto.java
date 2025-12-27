package com.aigreentick.services.template.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class TemplateTextDto {
    private Integer variableIndex;
    private String text;
    private String type;
}
