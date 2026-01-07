package com.aigreentick.services.template.dto.request;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateCarouselExampleRequest {
    private List<String> headerHandle;
    private List<String> headerText;
    List<List<String>> bodyText;
}
