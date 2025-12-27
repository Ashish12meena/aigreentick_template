package com.aigreentick.services.template.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ComponentType {
    BODY("BODY"),
    FOOTER("FOOTER"),
    BUTTONS("BUTTONS"),
    HEADER("HEADER"),
    CAROUSEL("CAROUSEL"),
    LIMITED_TIME_OFFER("LIMITED_TIME_OFFER");

    public static class constants {
        public static final String BODY = "BODY";
        public static final String FOOTER = "FOOTER";
        public static final String BUTTONS = "BUTTONS";
        public static final String HEADER = "HEADER";
        public static final String CAROUSEL = "CAROUSEL";
        public static final String LIMITED_TIME_OFFER = "LIMITED_TIME_OFFER";
    }

    private final String value;

    ComponentType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ComponentType fromValue(String value) {
        for (ComponentType type : ComponentType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown component type: " + value);
    }
}

