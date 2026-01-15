package com.aigreentick.services.template.service.impl.common;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.model.common.Wallet;
import com.aigreentick.services.template.repository.common.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl {
    private final WalletRepository walletRepository;

    public Wallet save(Wallet wallet){
        return walletRepository.save(wallet);
    }
}
