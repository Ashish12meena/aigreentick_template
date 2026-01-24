package com.aigreentick.services.template.dto.request.template.create;

import java.util.List;

import lombok.Data;


@Data
public class VariableDefaultComponentDto {
    private String type;  // HEADER, BODY, BUTTONS, CAROUSEL
    
    // For HEADER, BODY
    private List<String> attributes;
    
    // For BUTTONS
    private List<VariableDefaultButtonDto> buttons;
    
    // For CAROUSEL
    private List<VariableDefaultCardDto> cards;
}
