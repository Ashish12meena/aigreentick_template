package com.aigreentick.services.template.dto.request;

import java.util.List;

import lombok.Data;

@Data
public class TemplateExampleRequest {
    List<String> headerText; // for TEXT format
    List<String> headerHandle;
    List<List<String>> bodyText;
}
