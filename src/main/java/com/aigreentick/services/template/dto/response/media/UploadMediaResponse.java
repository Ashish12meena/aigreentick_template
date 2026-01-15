package com.aigreentick.services.template.dto.response.media;


import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class UploadMediaResponse {
    @JsonProperty("h")
    private String facebookImageUrl;
}