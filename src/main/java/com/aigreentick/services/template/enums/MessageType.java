package com.aigreentick.services.template.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum MessageType {
    TEXT("text"),
    IMAGE("image"),
    VIDEO("video"),
    FILE("file"),
    AUDIO("audio"),
    STICKER("sticker"),
    LOCATION("location"),
    CONTACTS("contacts"),
    SYSTEM("system"),
    TEMPLATE("template"),
    INTERACTIVE("interactive"),
    REACTION("reaction");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static MessageType fromValue(String value) {
        for (MessageType type : MessageType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MessageType: " + value);
    }
}