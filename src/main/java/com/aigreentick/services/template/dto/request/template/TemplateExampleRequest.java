package com.aigreentick.services.template.dto.request.template;

import java.util.List;

import lombok.Data;

@Data
public class TemplateExampleRequest {
    List<String> headerText; // for TEXT format
    List<String> headerHandle;
    List<List<String>> bodyText;
}
