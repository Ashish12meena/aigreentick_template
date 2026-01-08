package com.aigreentick.services.template.dto.request;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.Future;
import lombok.Data;

@Data
public class SendTemplateRequestDto {
    private Long temlateId;
    private String campanyName;
    private Long countryId;
    private List<String> mobileNumbers;

    private String languageCode;
    private String otp;
    private String copyCode;
    // private String templateType;
    private boolean isFullyPrameterized;
    private String defaultValue;
    private Map<String, String> parameters; 
    private Long expirationTimeMs;
    private Boolean isMedia;
    private long mediaId;
    private String mediaUrl;
    private String mediaType;
    List<String> mediaIdsForCarosel;
    private String catalogId; 
    private List<String> productRetailerIds;

    @Future(message = "Schedule date must be in the future")
    private Instant scheduledAt;
}
