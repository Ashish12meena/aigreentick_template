package com.aigreentick.services.template.enums;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Enum for template_status
public enum TemplateStatus {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED"),
    FAILED("FAILED");

      private final String value;

    TemplateStatus(String value) {
        this.value = value;
    }

    @JsonValue   
    public String getValue() {
        return value;  
    }
     // Flexible deserialization: case-insensitive mapping
    @JsonCreator
    public static TemplateStatus fromValue(String input) {
        if (input == null) {
            return null;
        }
        for (TemplateStatus category : TemplateStatus.values()) {
            if (category.value.equalsIgnoreCase(input)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown category: " + input);
    }

     // static constants for reuse
    public static class constants {
        public static final String PENDING = "PENDING";
        public static final String APPROVED = "APPROVED";
        public static final String REJECTED = "REJECTED";
        public static final String FAILED="FAILED";
    }
}

