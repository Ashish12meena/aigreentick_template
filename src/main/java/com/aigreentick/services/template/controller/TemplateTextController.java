package com.aigreentick.services.template.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.dto.request.TemplateTextResponseDto;
import com.aigreentick.services.template.dto.request.TemplateTextUpdateDefaultRequest;
import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.service.impl.TemplateTextServiceImpl;

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
    public ResponseEntity<?> getTemplateText(@PathVariable Long templateId) {
        log.info("Fetching variables for templateId: {}", templateId);

        Long userId = 1L; // Replace with actual user from security context

        List<TemplateTextResponseDto> variables = templateTextService.getTemplateVariables(templateId, userId);

        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Template variables fetched successfully",
                variables));
    }

    /**
     * Update default values for template variables
     */
    @PutMapping("/{templateId}/variables/defaults")
    public ResponseEntity<?> updateVariableDefaults(
            @PathVariable Long templateId,
            @Valid @RequestBody TemplateTextUpdateDefaultRequest request) {

        log.info("Updating default values for templateId: {}", templateId);

        Long userId = 1L; // Replace with actual user from security context

        List<TemplateTextResponseDto> updatedVariables = templateTextService
                .updateVariableDefaults(templateId, userId, request.getDefaults());

        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Default values updated successfully",
                updatedVariables));
    }

    /**
     * Update a single variable's default value
     */
    @PutMapping("/{templateId}/variables/{variableId}/default")
    public ResponseEntity<?> updateSingleVariableDefault(
            @PathVariable Long templateId,
            @PathVariable Long variableId,
            @RequestBody SingleVariableDefaultRequest request) {

        log.info("Updating default value for variableId: {} in templateId: {}", variableId, templateId);

        Long userId = 1L; // Replace with actual user from security context

        TemplateTextResponseDto updatedVariable = templateTextService
                .updateSingleVariableDefault(templateId, variableId, userId, request.getDefaultValue());

        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Default value updated successfully",
                updatedVariable));
    }

    /**
     * Clear all default values for a template
     */
    @PutMapping("/{templateId}/variables/defaults/clear")
    public ResponseEntity<?> clearAllDefaults(@PathVariable Long templateId) {
        log.info("Clearing all default values for templateId: {}", templateId);

        Long userId = 1L; // Replace with actual user from security context

        templateTextService.clearAllDefaults(templateId, userId);

        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "All default values cleared successfully",
                null));
    }

    // Inner class for single variable update
    @lombok.Data
    public static class SingleVariableDefaultRequest {
        private String defaultValue;
    }
}
