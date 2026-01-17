package com.aigreentick.services.template.dto.request.template.csv;

import java.util.Map;

import lombok.Data;

@Data
public class CarouselButtonDto {

    private Long id;
    private String type; // QUICK_REPLY, URL
    private String text;
    private String url; // nullable

    // only present for URL buttons
    private Map<String, String> variables;
}