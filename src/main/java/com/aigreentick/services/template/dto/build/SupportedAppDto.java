package com.aigreentick.services.template.dto.build;



import lombok.Data;

@Data
public class SupportedAppDto {
    private String packageName;
    private String signatureHash;
}
