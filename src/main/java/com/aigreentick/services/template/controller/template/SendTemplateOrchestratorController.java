package com.aigreentick.services.template.controller.template;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.request.template.SendTemplateRequestDto;
import com.aigreentick.services.template.dto.response.common.ResponseMessage;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.template.SendTemplateOrchestratorServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("api/v1/template")
@RequiredArgsConstructor
@Slf4j
public class SendTemplateOrchestratorController {

    private final SendTemplateOrchestratorServiceImpl sendTemplateOrchestratorServiceImpl;

    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SendTemplateRequestDto request) {

        log.info("Broadcasting template for userId={}", userId);

        TemplateResponseDto response =
                sendTemplateOrchestratorServiceImpl.broadcastTemplate(request, userId);

        return ResponseEntity.ok(
                new ResponseMessage<>(
                        ResponseStatus.SUCCESS.name(),
                        TemplateConstants.Messages.TEMPLATE_CREATED,
                        response
                )
        );
    }
}

