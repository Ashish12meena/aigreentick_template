package com.aigreentick.services.template.repository.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.common.Wallet;


@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    
    
}
