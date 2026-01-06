package com.aigreentick.services.template.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.dto.request.TemplateRequest;
import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.dto.response.TemplateSyncStats;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.TemplateOrchestratorServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("api/v1/template")
@RequiredArgsConstructor
@Slf4j
public class TemplateOrchestratorController {
    private final TemplateOrchestratorServiceImpl templateOrchestratorServiceImpl;

    @PostMapping("/create")
    public ResponseEntity<?> createTemplate(
            @RequestBody TemplateRequest request) {

        log.info("Creating template ");

        Long userId = 1L;

        TemplateResponseDto response = templateOrchestratorServiceImpl.createTemplate(request, userId);

        return ResponseEntity
                .ok(new ResponseMessage<>(ResponseStatus.SUCCESS.name(), TemplateConstants.Messages.TEMPLATE_CREATED,
                        response));
    }

    @GetMapping("/sync-my-templates")
    public ResponseEntity<?> syncTemplateWithFacebook() {

        Long userId = 1L;
        log.info("Syncing templates for userId: {}", userId);

        TemplateSyncStats response = templateOrchestratorServiceImpl.syncTemplatesWithFacebook(userId);

        return ResponseEntity.ok(
                new ResponseMessage<>(ResponseStatus.SUCCESS.name(),
                        TemplateConstants.Messages.TEMPLATES_FETCHED, response));
    }

   
}
