package com.aigreentick.services.template.dto.request;

import lombok.Data;

@Data
public class BroadcastDispatchItemDto {

    private Long broadcastId;

    private String mobileNo;

    private String payload;
}
