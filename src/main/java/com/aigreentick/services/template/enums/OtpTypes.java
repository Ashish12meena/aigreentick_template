package com.aigreentick.services.template.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OtpTypes {
    ONE_TAP("ONE_TAP"),
    COPY_CODE("COPY_CODE"),
    ZERO_TAP("ZERO_TAP");

     public static class constants {
        public static final String ONE_TAP = "ONE_TAP";
        public static final String COPY_CODE = "COPY_CODE";
        public static final String ZERO_TAP = "ZERO_TAP";
    }


    private final String value;

    OtpTypes(String value) {
         this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OtpTypes fromValue(String value) {
        for (OtpTypes type : OtpTypes.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown component type: " + value);
    }
}

