package com.aigreentick.services.template.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.dto.request.SendTemplateRequestDto;
import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.SendTemplateOrchestratorServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("api/v1/template")
@RequiredArgsConstructor
@Slf4j
public class SendTemplateOrchestratorController {
    private final SendTemplateOrchestratorServiceImpl sendTemplateOrchestratorServiceImpl;

    @PostMapping("/broadcast")
    public ResponseEntity<?> createTemplate(
            @RequestBody SendTemplateRequestDto request) {

        log.info("Creating template ");

        Long userId = 1L;

        TemplateResponseDto response = sendTemplateOrchestratorServiceImpl.broadcastTemplate(request, userId);

        return ResponseEntity
                .ok(new ResponseMessage<>(ResponseStatus.SUCCESS.name(), TemplateConstants.Messages.TEMPLATE_CREATED,
                        response));
    }
}
