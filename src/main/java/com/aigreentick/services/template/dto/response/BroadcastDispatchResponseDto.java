package com.aigreentick.services.template.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastDispatchResponseDto {
    private String status;
    private String message;
    private DispatchResultData data;
}