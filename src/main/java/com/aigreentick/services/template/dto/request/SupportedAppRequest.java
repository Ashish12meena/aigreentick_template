package com.aigreentick.services.template.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class SupportedAppRequest {
    private String packageName;
    private String signatureHash;
}
