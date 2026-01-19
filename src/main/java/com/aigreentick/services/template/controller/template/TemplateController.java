package com.aigreentick.services.template.controller.template;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.constants.TemplateConstants;
import com.aigreentick.services.template.dto.request.template.TempalatePaginationRequestDto;
import com.aigreentick.services.template.dto.response.common.ResponseMessage;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.model.template.Template;
import com.aigreentick.services.template.service.impl.template.TemplateServiceImpl;

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

        @DeleteMapping("/delete/all")
        public ResponseEntity<?> deleteAllTemplate(
                        @RequestHeader("X-User-Id") Long userId) {

                templateService.deleteAllTemplatesByUserId(userId);

                return ResponseEntity.ok(
                                new ResponseMessage<>(
                                                ResponseStatus.SUCCESS.name(),
                                                "Templates deleted",
                                                null));
        }

        /**
         * Get all templates for the user with optional search and pagination.
         */
        @GetMapping(TemplateConstants.Paths.MY_TEMPLATES)
        public ResponseEntity<?> getUserTemplates(
                        @RequestHeader("X-User-Id") Long userId,
                        @RequestParam(name = "search", required = false) String search,
                        @RequestParam(name = "status", required = false) String status,
                        @Valid TempalatePaginationRequestDto pagination) {

                log.info("Fetching templates for userId={}", userId);

                Page<TemplateResponseDto> templates = templateService.getTemplatesForUser(
                                userId,
                                status,
                                search,
                                pagination.getPage(),
                                pagination.getSize());

                return ResponseEntity.ok(
                                new ResponseMessage<>(
                                                ResponseStatus.SUCCESS.name(),
                                                TemplateConstants.Messages.TEMPLATES_FETCHED,
                                                templates));
        }

}
