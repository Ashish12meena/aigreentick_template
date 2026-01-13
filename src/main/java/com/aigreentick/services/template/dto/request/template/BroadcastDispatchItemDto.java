package com.aigreentick.services.template.dto.request.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastDispatchItemDto {
    private Long broadcastId;
    private String mobileNo;
    private String payload;
}