package com.aigreentick.services.template.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.request.TempalatePaginationRequestDto;
import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.model.Template;
import com.aigreentick.services.template.service.impl.TemplateServiceImpl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("api/v1/template")
@RequiredArgsConstructor
@Slf4j
public class TemplateController {
    private final TemplateServiceImpl templateService;

    @GetMapping(TemplateConstants.Paths.TEMPLATE_BY_ID)
    public ResponseEntity<?> getTemplate(@PathVariable Long id) {
        Template response = templateService.getTemplateById(id);

        return ResponseEntity.ok(
                new ResponseMessage<>(
                        ResponseStatus.SUCCESS.name(),
                        TemplateConstants.Messages.TEMPLATES_FETCHED,
                        response));

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplateById(id);

        return ResponseEntity.ok(
                new ResponseMessage<>(
                        ResponseStatus.SUCCESS.name(),
                        "Template deleted",
                        null));

    }

    /**
     * Get all templates for the project with optional search and pagination.
     */
    @GetMapping(TemplateConstants.Paths.MY_TEMPLATES)
    public ResponseEntity<?> getUserTemplates(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status,
            @Valid TempalatePaginationRequestDto pagination) {

        Long userId = 1L;
        log.info("Fetching templates for projectId: {}", userId);

        Page<TemplateResponseDto> templates = templateService.getTemplatesForUser(
                userId, status, search, pagination.getPage(), pagination.getSize());

        return ResponseEntity.ok(
                new ResponseMessage<>(ResponseStatus.SUCCESS.name(),
                        TemplateConstants.Messages.TEMPLATES_FETCHED, templates));
    }
}
