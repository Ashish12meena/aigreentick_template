package com.aigreentick.services.template.dto.request.template;


import java.util.List;

import com.aigreentick.services.template.dto.request.WhatsappAccountInfoDto;

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
