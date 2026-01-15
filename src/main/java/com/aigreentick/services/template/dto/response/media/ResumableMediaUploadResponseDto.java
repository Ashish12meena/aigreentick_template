package com.aigreentick.services.template.dto.response.media;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumableMediaUploadResponseDto {
    private String fileName;
    private long fileSize;
    private String mimeType;
    private String mediaUrl;
    private String sessionId;   
}
