package com.aigreentick.services.template.dto.build;


import java.util.List;

import lombok.Data;

@Data
public class Component {
    private String type;
    private List<Parameter> parameters;
    private String subType;
    private String index;
    List<Card> cards;

}
