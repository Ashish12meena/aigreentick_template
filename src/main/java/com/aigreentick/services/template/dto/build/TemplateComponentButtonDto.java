package com.aigreentick.services.template.dto.build;

import java.util.List;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class TemplateComponentButtonDto {
    private String type; // QUICK_REPLY, URL, PHONE_NUMBER, OTP
    private String otpType;
    private String phoneNumber;
    private String text;
    private int index;
    private String url;
    private String autofillText;
    List<String> example;
    List<SupportedAppDto> supportedApps;
}
