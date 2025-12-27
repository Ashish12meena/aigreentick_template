package com.aigreentick.services.template.dto.request;

import java.util.List;

import com.aigreentick.services.template.enums.TemplateStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class TemplateRequest {
     private String name;

    private String category;

    private String language;

    private TemplateStatus status;  // Pending, Approved, Rejected

    private String rejectionReason;

    private String previousCategory;

    private String metaTemplateId;

    private String submissionPayload;

    private List<TemplateComponentRequest> components;

    private List<TemplateTextRequest> texts;

    private String response;

    private Long userId;

    private String componentsJson;
}
