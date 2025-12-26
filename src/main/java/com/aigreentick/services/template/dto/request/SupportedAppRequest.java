package com.aigreentick.services.template.dto.request;

import lombok.Data;

@Data
public class SupportedAppRequest {
    private String packageName;
    private String signatureHash;
}
