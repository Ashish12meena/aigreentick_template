package com.aigreentick.services.template.service.impl.template;


import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.template.client.adapter.UserService;
import com.aigreentick.services.template.client.adapter.WhatsappClientImpl;
import com.aigreentick.services.template.dto.response.common.AccessTokenCredentials;
import com.aigreentick.services.template.dto.response.common.FacebookApiResponse;
import com.aigreentick.services.template.dto.response.media.ResumableMediaUploadResponseDto;
import com.aigreentick.services.template.dto.response.media.UploadMediaResponse;
import com.aigreentick.services.template.dto.response.media.UploadSessionResponse;
import com.aigreentick.services.template.exceptions.ExternalServiceException;
import com.aigreentick.services.template.exceptions.MediaUploadException;
import com.aigreentick.services.template.mapper.ResumableMediaMapper;
import com.aigreentick.services.template.model.template.MediaResumable;
import com.aigreentick.services.template.repository.template.ResumableMediaRepository;
import com.aigreentick.services.template.util.helper.FileMetaData;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumableMediaUploadServiceImpl {

    private final WhatsappClientImpl whatsappUploadFile;
    private final ResumableMediaRepository mediaRepository;
    private final ResumableMediaMapper mediaMapper;
    private final UserService userService;

    @Value("${apiVersion}")
    private String apiVersion;

    public ResumableMediaUploadResponseDto uploadMedia(MultipartFile file, Long userId) {
        log.info("Uploading media for userId: {}", userId);

        if (userId == null ) {
            throw new IllegalArgumentException("userId ID is required");
        }

        AccessTokenCredentials accessTokenCredentials = userService.getWabaAppAccessToken(userId);
        String offset = "0";
        
        try {
            FileMetaData fileMeta = extractFileDetails(file);
            File fullFile = convertMultipartToFile(file);

            FacebookApiResponse<UploadSessionResponse> sessionResponse = whatsappUploadFile.initiateUploadSession(
                    fileMeta.getFileName(), fileMeta.getFileSize(), fileMeta.getMimeType(),
                    accessTokenCredentials.getWabaId(), accessTokenCredentials.getAccessToken());

            if (!sessionResponse.isSuccess()) {
                throw new ExternalServiceException(sessionResponse.getErrorMessage());
            }

            String sessionId = sessionResponse.getData().getUploadSessionId();

            FacebookApiResponse<UploadMediaResponse> uploadMediaResponse = tryUploadToFacebook(
                    sessionId, fullFile, accessTokenCredentials.getAccessToken(), apiVersion, offset);

            if (!uploadMediaResponse.isSuccess()) {
                throw new ExternalServiceException(sessionResponse.getErrorMessage());
            }

            MediaResumable media = saveMediaRecord(userId, sessionId, fileMeta,
                    uploadMediaResponse.getData().getFacebookImageUrl());
            
            return mediaMapper.toDto(media);

        } catch (IOException ex) {
            throw new MediaUploadException("Failed to upload file", ex);
        }
    }

    private FacebookApiResponse<UploadMediaResponse> tryUploadToFacebook(
            String sessionId, File file, String accessToken, String apiVersion, String offset) {
        try {
            return whatsappUploadFile.uploadResumableMediaToFacebook(sessionId, file, accessToken, offset);
        } catch (Exception e) {
            log.warn("Initial upload failed, attempting to resume with offset...", e);
            try {
                String newOffset = whatsappUploadFile
                        .getUploadOffset(sessionId, accessToken)
                        .getFileOffset();

                return whatsappUploadFile.uploadResumableMediaToFacebook(sessionId, file, accessToken, newOffset);
            } catch (IOException retryEx) {
                throw new MediaUploadException("Retry upload failed", retryEx);
            }
        }
    }

    private FileMetaData extractFileDetails(MultipartFile file) {
        return new FileMetaData(
                Objects.requireNonNull(file.getOriginalFilename()),
                file.getSize(),
                file.getContentType());
    }

    private MediaResumable saveMediaRecord(Long userId, String sessionId, FileMetaData meta, String handle) {
        MediaResumable media = mediaMapper.toEntity(userId, sessionId, meta, handle);
        return mediaRepository.save(media);
    }

    private File convertMultipartToFile(MultipartFile file) throws IOException {
        File convFile = File.createTempFile("upload_", Objects.requireNonNull(file.getOriginalFilename()));
        file.transferTo(convFile);
        return convFile;
    }
}