package com.aigreentick.services.template.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchResultData {
    private int totalDispatched;
    private int failedCount;
    private String message;
}