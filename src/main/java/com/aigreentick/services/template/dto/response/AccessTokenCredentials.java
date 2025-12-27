package com.aigreentick.services.template.dto.response;


import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccessTokenCredentials {
    private final String wabaId; // WABA ID or PhoneNumber ID
    private final String accessToken;
}
