package com.aigreentick.services.template.dto.build;

import java.util.List;

import lombok.Data;

@Data
public class SendableTemplate {
    private String name;
    private Language language;
    List<Component> components;
}
    