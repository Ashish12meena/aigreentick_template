package com.aigreentick.services.template.dto.request.template.csv;

import java.util.List;

import lombok.Data;

@Data
public class VariableGroupDto {

    private Long mobile; // optional
    private List<VariableDto> variable;
}

