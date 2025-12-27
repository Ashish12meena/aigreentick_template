package com.aigreentick.services.template.dto.request;

import java.util.List;

import com.aigreentick.services.template.enums.ButtonTypes;
import com.aigreentick.services.template.enums.OtpTypes;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;


@Data
@JsonInclude(JsonInclude.Include.NON_NULL) 
public class TemplateComponentButtonRequest {
        /**
     * Type of the button.
     * Allowed values: QUICK_REPLY, URL, PHONE_NUMBER, OTP
     */
    private ButtonTypes type;

    /**
     * Type of OTP (if button type is OTP). Can be null for other button types.
     */
    private OtpTypes otpType;

    private String phoneNumber;

    private String text;

    /**
     * Index of the button within the component (used for ordering).
     */
    private int index;

    /**
     * URL for URL type buttons. Can be null for other types.
     */
    private String url;

    /**
     * Autofill text to pre-fill in the message for QUICK_REPLY buttons.
     */
    private String autofillText;

    List<String> example;

    List<SupportedAppRequest> supportedApps;
}
