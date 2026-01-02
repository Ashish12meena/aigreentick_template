package com.aigreentick.services.template.dto.build;


import java.util.List;


import lombok.Data;

@Data
public class CarouselComponent {
    private String type;
    private List<Parameter> parameters;
    private String subType;
    private Integer index;
}
