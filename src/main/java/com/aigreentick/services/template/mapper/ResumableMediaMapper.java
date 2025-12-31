package com.aigreentick.services.template.mapper;

import org.springframework.stereotype.Component;

import com.aigreentick.services.template.dto.FileMetaData;
import com.aigreentick.services.template.dto.response.ResumableMediaUploadResponseDto;
import com.aigreentick.services.template.model.MediaResumable;



@Component
public class ResumableMediaMapper {

    public ResumableMediaUploadResponseDto toDto(MediaResumable media) {
        return ResumableMediaUploadResponseDto.builder()
                .fileName(media.getFileName())
                .fileSize(media.getFileSize())
                .mimeType(media.getMimeType())
                .mediaUrl(media.getMediaHandle())
                .sessionId(media.getSessionId())
                .build();
    }

    public MediaResumable toEntity(Long userId, String sessionId, FileMetaData meta, String handle) {
        return MediaResumable.builder()
                .fileName(meta.getFileName())
                .fileSize(meta.getFileSize())
                .mimeType(meta.getMimeType())
                .mediaHandle(handle)
                .sessionId(sessionId)
                .userId(userId)  // Changed from uploadedByUserId
                .build();
    }

}