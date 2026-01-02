package com.aigreentick.services.template.dto.build;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LimitedTimeOffer {
    private Long expirationTimeMs;
}
