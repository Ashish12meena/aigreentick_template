package com.aigreentick.services.template.enums;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TemplateCategory {
    MARKETING("MARKETING"),
    UTILITY("UTILITY"),
    AUTHENTICATION("AUTHENTICATION");

    private final String value;

    TemplateCategory(String value) {
        this.value = value;
    }

    @JsonValue   
    public String getValue() {
        return value; 
    }
     // Flexible deserialization: case-insensitive mapping
    @JsonCreator
    public static TemplateCategory fromValue(String input) {
        if (input == null) {
            return null;
        }
        for (TemplateCategory category : TemplateCategory.values()) {
            if (category.value.equalsIgnoreCase(input)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category: " + input);
    }

     // static constants for reuse
    public static class constants {
        public static final String AUTHENTICATION = "AUTHENTICATION";
        public static final String MARKETING = "MARKETING";
        public static final String UTILITY = "UTILITY";
    }

}
