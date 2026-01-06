package com.aigreentick.services.template.dto.request;

import java.util.List;

import com.aigreentick.services.template.enums.TemplateStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) 
@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateRequest {

     private String name;

    private String category;

    private String language;

    private TemplateStatus status;  // Pending, Approved, Rejected

    private String rejectionReason;

    private String previousCategory;

    @JsonProperty("id")
    private String metaTemplateId;

    private String submissionPayload;

    private List<TemplateComponentRequest> components;

    // private List<TemplateTextRequest> texts;

    private String response;

}
