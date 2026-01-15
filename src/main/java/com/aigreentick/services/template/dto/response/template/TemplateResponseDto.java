package com.aigreentick.services.template.dto.response.template;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TemplateResponseDto {
    private Long id;
    private String name;
    private String status;
    private String category;
    private String language;
    private String metaTemplateId;
    private String errorMessage;
    private JsonNode errorPayload;

    public TemplateResponseDto(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public TemplateResponseDto(String errorMessage, JsonNode errorPayload) {
        this.errorMessage = errorMessage;
        this.errorPayload = errorPayload;
    }
}