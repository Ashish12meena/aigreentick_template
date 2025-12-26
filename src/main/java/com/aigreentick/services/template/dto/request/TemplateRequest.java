package com.aigreentick.services.template.dto.request;

import java.util.List;

import com.aigreentick.services.template.enums.TemplateStatus;
import lombok.Data;

@Data
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

    private String componentsJson;
}
