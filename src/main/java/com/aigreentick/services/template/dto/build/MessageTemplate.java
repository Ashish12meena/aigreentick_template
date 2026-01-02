package com.aigreentick.services.template.dto.build;

import com.aigreentick.services.template.enums.MessageType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageTemplate {

    @Builder.Default
    private String messagingProduct = "whatsapp";
    
    @Builder.Default
    private String recipientType = "individual";

    @NotNull
    private String to;

    @NotNull
    private MessageType type;

    private SendableTemplate template;

}

