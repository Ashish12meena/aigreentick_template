package com.aigreentick.services.template.dto.response.common;

import java.util.Map;

import lombok.Data;

@Data
public class PhoneBookResponseDto {
    private Map<String, Map<String, String>> data;
}
