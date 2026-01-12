package com.aigreentick.services.template.client.adapter;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.dto.response.AccessTokenCredentials;
import com.aigreentick.services.template.model.WhatsappAccount;
import com.aigreentick.services.template.service.impl.WhatsappAccountServiceImpl;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final WhatsappAccountServiceImpl whatsappAccountServiceImpl;

    public AccessTokenCredentials getWabaAccessToken(Long userId) {
        WhatsappAccount whatsappAccount = whatsappAccountServiceImpl.getActiveAccountByUserId(userId);

        return AccessTokenCredentials.builder()
        .wabaId(whatsappAccount.getWhatsappBizId())
        .accessToken(whatsappAccount.getParmenentToken())
        .build();
    }

    public AccessTokenCredentials getWabaAppAccessToken(Long userId) {
        WhatsappAccount whatsappAccount = whatsappAccountServiceImpl.getActiveAccountByUserId(userId);
        return AccessTokenCredentials.builder()
        .wabaId(whatsappAccount.getWhatsappNoId())
        .accessToken(whatsappAccount.getParmenentToken())
        .build();
    }

}
