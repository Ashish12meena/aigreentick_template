package com.aigreentick.services.template.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappAccountInfoDto {
    private String phoneNumberId;
    private String accessToken;
}