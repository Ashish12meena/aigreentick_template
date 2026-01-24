package com.aigreentick.services.template.dto.request.template.create;

import java.util.List;

import lombok.Data;

@Data
public class VariableDefaultCardDto {
    private List<VariableDefaultComponentDto> components;
}
