package com.aigreentick.services.template.dto.request;


import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchRequestDto {

    private List<BroadcastDispatchItemDto> items;

    private WhatsappAccountInfoDto accountInfo;
}
