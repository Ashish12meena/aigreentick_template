package com.aigreentick.services.template.dto.build;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    @Builder.Default
    private String messagingProduct = "whatsapp";
    
    @Builder.Default
    private String recipientType = "individual";

    @NotNull
    private String to;

    @NotNull
    @Builder.Default
    private String type = "template";

    private SendableTemplate template;

}

