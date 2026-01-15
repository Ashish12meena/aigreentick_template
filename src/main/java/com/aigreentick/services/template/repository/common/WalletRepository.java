package com.aigreentick.services.template.repository.common;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.template.model.common.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    
    
}
