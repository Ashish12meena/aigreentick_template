package com.aigreentick.services.template.client.adapter;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.dto.response.common.AccessTokenCredentials;
import com.aigreentick.services.template.model.account.WhatsappAccount;
import com.aigreentick.services.template.service.impl.account.WhatsappAccountServiceImpl;

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
