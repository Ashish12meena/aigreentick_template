package com.aigreentick.services.template.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BroadcastType {
    NORMAL("NORMAL"),
    CSV("CSV");

    private final String value;

    BroadcastType(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;   
    }

    @JsonCreator
    public static BroadcastType fromValue(String value) {
        for (BroadcastType type : BroadcastType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Button type: " + value);
    }
}
