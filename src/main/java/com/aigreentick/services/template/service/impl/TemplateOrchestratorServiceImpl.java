package com.aigreentick.services.template.service.impl;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.client.adapter.UserService;
import com.aigreentick.services.template.client.adapter.WhatsappClientImpl;
import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.dto.request.TemplateRequest;
import com.aigreentick.services.template.dto.response.AccessTokenCredentials;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.mapper.TemplateMapper;
import com.aigreentick.services.template.model.Template;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateOrchestratorServiceImpl {
    private final TemplateServiceImpl templateServiceImpl;
    private final TemplateMapper templateMapper;
    private final WhatsappClientImpl whatsappClientImpl;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public TemplateResponseDto createTemaplate(CreateTemplateResponseDto request) {
        log.info("Creating template for userId: {}", request.getTemplate().getUserId());

        // Extract the template details and check if not duplicate
        TemplateRequest templateRequest = request.getTemplate();
        templateServiceImpl.checkDuplicateTemplate(templateRequest.getName(), request.getTemplate().getUserId());

        // Fetch WABA credentials
        AccessTokenCredentials accessTokenCredentials = userService.getWabaAccessToken();

        // Serialize the template request to JSON
        String jsonRequest = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .writeValueAsString(templateRequest);

        Template template = templateMapper.maptoEntity(request);

        templateServiceImpl.save(template);

        return templateMapper.mapToTemplateResponse(template);

    }

}
