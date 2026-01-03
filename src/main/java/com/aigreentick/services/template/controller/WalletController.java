package com.aigreentick.services.template.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.model.Wallet;
import com.aigreentick.services.template.service.impl.WalletServiceImpl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Slf4j
public class WalletController {
    private final WalletServiceImpl walletServiceImpl;

    @PostMapping
    public ResponseEntity<?> createWallet(@Valid @RequestBody Wallet walllet) {
        log.info("amount in wallet: {}", walllet.getAmount());

        Wallet savedReport = walletServiceImpl.save(walllet);

        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Wallet created successfully",
                savedReport));
    }
}
