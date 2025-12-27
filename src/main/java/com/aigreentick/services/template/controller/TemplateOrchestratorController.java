package com.aigreentick.services.template.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.dto.request.CreateTemplateResponseDto;
import com.aigreentick.services.template.service.impl.TemplateOrchestratorServiceImpl;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/v1/template")
@RequiredArgsConstructor
public class TemplateOrchestratorController {
    private final TemplateOrchestratorServiceImpl templateOrchestratorServiceImpl;

    @PostMapping("/create")
    public void createTemaplate(@RequestBody CreateTemplateResponseDto request){
        templateOrchestratorServiceImpl.createTemaplate(request);
    }
}
