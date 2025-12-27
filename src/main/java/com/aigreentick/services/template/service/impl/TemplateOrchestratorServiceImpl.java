package com.aigreentick.services.template.service.impl;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.dto.response.TemplateResponse;
import com.aigreentick.services.template.mapper.TemplateMapper;
import com.aigreentick.services.template.model.Template;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class TemplateOrchestratorServiceImpl {
    private final TemplateServiceImpl templateServiceImpl;
    private final TemplateMapper templateMapper;

    public TemplateResponse createTemaplate(CreateTemplateResponseDto request) {
        Template template =  templateMapper.maptoEntity(request);

        templateServiceImpl.save(template);

        return templateMapper.mapToTemplateResponse(template);

    }


}
