package com.aigreentick.services.template.controller.media;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.aigreentick.services.template.dto.response.common.ResponseMessage;
import com.aigreentick.services.template.dto.response.media.ResumableMediaUploadResponseDto;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.template.ResumableMediaUploadServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/template/resumable/media")
@Slf4j
public class ResumableMediaController {
    private final ResumableMediaUploadServiceImpl mediaUploadService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMedia(
            @RequestPart("file") MultipartFile file) {

        Long userId = 1L;
        log.info("Uploading media for projectId: {}", userId);

        ResumableMediaUploadResponseDto responseDto = mediaUploadService.uploadMedia(file, userId);

        return ResponseEntity.ok(
                new ResponseMessage<>(ResponseStatus.SUCCESS.name(),
                        "Media Uploaded Successfully", responseDto));
    }
}
