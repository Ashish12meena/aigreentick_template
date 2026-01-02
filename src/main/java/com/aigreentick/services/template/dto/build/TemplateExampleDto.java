package com.aigreentick.services.template.dto.build;

import java.util.List;

import lombok.Data;

@Data
public class TemplateExampleDto {
    List<String> headerText; // for TEXT format
    List<String> headerHandle;
    List<List<String>> bodyText;
}
