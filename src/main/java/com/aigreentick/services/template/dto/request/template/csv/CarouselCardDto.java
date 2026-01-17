package com.aigreentick.services.template.dto.request.template.csv;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class CarouselCardDto {

    private Long id;
    private String body;
    private String imageUrl;
    private Integer cardIndex;

    // dynamic variables like { "1": "card1U1" }
    private Map<String, String> variables;

    private List<CarouselButtonDto> buttons;
}
