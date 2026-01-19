package com.aigreentick.services.template.controller.template;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.dto.request.template.TemplateTextResponseDto;
import com.aigreentick.services.template.dto.request.template.TemplateTextUpdateDefaultRequest;
import com.aigreentick.services.template.dto.response.common.ResponseMessage;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.template.TemplateTextServiceImpl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@RestController
@RequestMapping("/api/v1/template")
@RequiredArgsConstructor
@Slf4j
public class TemplateTextController {

    private final TemplateTextServiceImpl templateTextService;

    /**
     * Get all variables for a template
     */
    @GetMapping("/{templateId}/text")
    public ResponseEntity<?> getTemplateText(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long templateId) {

        log.info("Fetching variables for templateId={} userId={}", templateId, userId);

        List<TemplateTextResponseDto> variables =
                templateTextService.getTemplateVariables(templateId, userId);

        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Template variables fetched successfully",
                variables
        ));
    }

    /**
     * Update default values for template variables
     */
    @PutMapping("/{templateId}/variables/defaults")
    public ResponseEntity<?> updateVariableDefaults(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long templateId,
            @Valid @RequestBody TemplateTextUpdateDefaultRequest request) {

        log.info("Updating default values for templateId={} userId={}", templateId, userId);

        List<TemplateTextResponseDto> updatedVariables =
                templateTextService.updateVariableDefaults(
                        templateId,
                        userId,
                        request.getDefaults()
                );

        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Default values updated successfully",
                updatedVariables
        ));
    }

    /**
     * Update a single variable's default value
     */
    @PutMapping("/{templateId}/variables/{variableId}/default")
    public ResponseEntity<?> updateSingleVariableDefault(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long templateId,
            @PathVariable Long variableId,
            @RequestBody SingleVariableDefaultRequest request) {

        log.info("Updating default value for variableId={} templateId={} userId={}",
                variableId, templateId, userId);

        TemplateTextResponseDto updatedVariable =
                templateTextService.updateSingleVariableDefault(
                        templateId,
                        variableId,
                        userId,
                        request.getDefaultValue()
                );

        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Default value updated successfully",
                updatedVariable
        ));
    }

    /**
     * Clear all default values for a template
     */
    @PutMapping("/{templateId}/variables/defaults/clear")
    public ResponseEntity<?> clearAllDefaults(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long templateId) {

        log.info("Clearing all default values for templateId={} userId={}", templateId, userId);

        templateTextService.clearAllDefaults(templateId, userId);

        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "All default values cleared successfully",
                null
        ));
    }

    @lombok.Data
    public static class SingleVariableDefaultRequest {
        private String defaultValue;
    }
}
