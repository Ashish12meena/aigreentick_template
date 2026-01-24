package com.aigreentick.services.template.dto.request.template.create;

import java.util.List;

import lombok.Data;

@Data
public class VariableDefaultButtonDto {
    private String type;  // URL (only URL buttons have variables)
    private List<String> attributes;
}

