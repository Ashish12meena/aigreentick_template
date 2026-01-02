package com.aigreentick.services.template.dto.build;



import java.util.List;

import lombok.Data;

@Data
public class Card {
    private Integer cardIndex;
    private List<CarouselComponent> components;
}
