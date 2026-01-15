package com.aigreentick.services.template.dto.response.common;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Builder
public class AccessTokenCredentials {
    private final String wabaId; // WABA ID or PhoneNumber ID
    private final String accessToken;
}
