package com.aigreentick.services.template.controller.template;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.request.template.csv.SendTemplateByCsvRequestDto;
import com.aigreentick.services.template.dto.response.common.ResponseMessage;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.template.SendTemplateByCSVOrchestratorServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("api/v1/template/csv")
@RequiredArgsConstructor
@Slf4j
public class SendTemplateByCSVOrchestratorController {
    private final SendTemplateByCSVOrchestratorServiceImpl sendTemplateByCSVOrchestratorServiceImpl;

        @PostMapping("/broadcast")
    public ResponseEntity<?> createTemplate(
            @RequestBody SendTemplateByCsvRequestDto request) {

        log.info("broadcasting template ");

        Long userId = 1L;

        TemplateResponseDto response = sendTemplateByCSVOrchestratorServiceImpl.broadcastTemplate(request, userId);

        return ResponseEntity
                .ok(new ResponseMessage<>(ResponseStatus.SUCCESS.name(), TemplateConstants.Messages.TEMPLATE_CREATED,
                        response));
    }
}
