package com.aigreentick.services.template.controller.template;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.request.template.TemplateRequest;
import com.aigreentick.services.template.dto.response.common.ResponseMessage;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.dto.response.template.TemplateSyncStats;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.template.TemplateOrchestratorServiceImpl;

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
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody TemplateRequest request) {

        log.info("Creating template for userId={}", userId);

        TemplateResponseDto response =
                templateOrchestratorServiceImpl.createTemplate(request, userId);

        return ResponseEntity.ok(
                new ResponseMessage<>(
                        ResponseStatus.SUCCESS.name(),
                        TemplateConstants.Messages.TEMPLATE_CREATED,
                        response
                )
        );
    }

    @GetMapping("/sync-my-templates")
    public ResponseEntity<?> syncTemplateWithFacebook(
            @RequestHeader("X-User-Id") Long userId) {

        log.info("Syncing templates for userId={}", userId);

        TemplateSyncStats response =
                templateOrchestratorServiceImpl.syncTemplatesWithFacebook(userId);

        return ResponseEntity.ok(
                new ResponseMessage<>(
                        ResponseStatus.SUCCESS.name(),
                        TemplateConstants.Messages.TEMPLATES_FETCHED,
                        response
                )
        );
    }
}
