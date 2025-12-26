package com.aigreentick.services.template.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ButtonTypes {
    URL("URL"),
    COPY_CODE("COPY_CODE"),
    QUICK_REPLY("QUICK_REPLY"),
    CATALOG("CATALOG"),
    MPM("MPM"),
    SPM("SPM"),
    OTP("OTP");

    private final String value;

    ButtonTypes(String value) {
        this.value = value;
    }
    
    @JsonValue
    public String getValue() {
        return value;   
    }

    @JsonCreator
    public static ButtonTypes fromValue(String value) {
        for (ButtonTypes type : ButtonTypes.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Button type: " + value);
    }
}
