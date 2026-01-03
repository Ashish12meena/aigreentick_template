package com.aigreentick.services.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.template.model.Wallet;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    
    
}
