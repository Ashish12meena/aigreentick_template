package com.aigreentick.services.template.controller.template;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.request.template.csv.SendTemplateByCsvRequestDto;
import com.aigreentick.services.template.dto.response.common.ResponseMessage;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.template.broadcast.SendTemplateByCSVOrchestratorServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("api/v1/template/csv")
@RequiredArgsConstructor
@Slf4j
public class SendTemplateByCSVOrchestratorController {

    private final SendTemplateByCSVOrchestratorServiceImpl sendTemplateByCSVOrchestratorServiceImpl;

    @PostMapping("/broadcast")
    public ResponseEntity<?> broadcast(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody SendTemplateByCsvRequestDto request) {

        log.info("Broadcasting template via CSV for userId={}", userId);

        TemplateResponseDto response = sendTemplateByCSVOrchestratorServiceImpl.broadcastTemplate(request, userId);

        return ResponseEntity.ok(
                new ResponseMessage<>(
                        ResponseStatus.SUCCESS.name(),
                        TemplateConstants.Messages.TEMPLATE_CREATED,
                        response));
    }

    @GetMapping("/sample")
    public String getSample() {
        return "sample fetched";
    }
}
