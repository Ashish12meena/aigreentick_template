package com.aigreentick.services.template.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.model.WhatsappAccount;
import com.aigreentick.services.template.service.impl.WhatsappAccountServiceImpl;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/whatsapp-account")
@RequiredArgsConstructor
@Slf4j
public class WhatsappAccountController {
    private final WhatsappAccountServiceImpl whatsappAccountService;

    @PostMapping
    public ResponseEntity<?> createAccount(@Valid @RequestBody WhatsappAccount account) {
        log.info("Creating WhatsApp account for userId: {}", account.getUserId());
        
        WhatsappAccount savedAccount = whatsappAccountService.save(account);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account created successfully",
                savedAccount));
    }

    @PutMapping
    public ResponseEntity<?> updateAccount(@Valid @RequestBody WhatsappAccount account) {
        log.info("Updating WhatsApp account with ID: {}", account.getId());
        
        WhatsappAccount updatedAccount = whatsappAccountService.save(account);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account updated successfully",
                updatedAccount));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAccountById(@PathVariable Long id) {
        log.info("Fetching WhatsApp account by ID: {}", id);
        
        WhatsappAccount account = whatsappAccountService.getAccountById(id);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account fetched successfully",
                account));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getAccountsByUserId(@PathVariable Long userId) {
        log.info("Fetching WhatsApp accounts for userId: {}", userId);
        
        List<WhatsappAccount> accounts = whatsappAccountService.getAccountsByUserId(userId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp accounts fetched successfully",
                accounts));
    }

    @GetMapping("/user/{userId}/active")
    public ResponseEntity<?> getActiveAccountByUserId(@PathVariable Long userId) {
        log.info("Fetching active WhatsApp account for userId: {}", userId);
        
        WhatsappAccount account = whatsappAccountService.getActiveAccountByUserId(userId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Active WhatsApp account fetched successfully",
                account));
    }

    @GetMapping("/waba/{wabaId}")
    public ResponseEntity<?> getAccountByWabaId(@PathVariable String wabaId) {
        log.info("Fetching WhatsApp account by WABA ID: {}", wabaId);
        
        WhatsappAccount account = whatsappAccountService.getAccountByWabaId(wabaId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account fetched successfully",
                account));
    }

    @GetMapping("/number-id/{numberId}")
    public ResponseEntity<?> getAccountByNumberId(@PathVariable String numberId) {
        log.info("Fetching WhatsApp account by number ID: {}", numberId);
        
        WhatsappAccount account = whatsappAccountService.getAccountByNumberId(numberId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account fetched successfully",
                account));
    }

    @GetMapping("/number/{number}")
    public ResponseEntity<?> getAccountByNumber(@PathVariable String number) {
        log.info("Fetching WhatsApp account by number: {}", number);
        
        WhatsappAccount account = whatsappAccountService.getAccountByNumber(number);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account fetched successfully",
                account));
    }

    @GetMapping("/all-active")
    public ResponseEntity<?> getAllActiveAccounts() {
        log.info("Fetching all active WhatsApp accounts");
        
        List<WhatsappAccount> accounts = whatsappAccountService.getAllActiveAccounts();
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Active WhatsApp accounts fetched successfully",
                accounts));
    }

    @GetMapping("/user/{userId}/has-active")
    public ResponseEntity<?> hasActiveAccount(@PathVariable Long userId) {
        log.info("Checking if userId: {} has active account", userId);
        
        boolean hasActive = whatsappAccountService.hasActiveAccount(userId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                hasActive ? "User has active account" : "User has no active account",
                hasActive));
    }

    @GetMapping("/user/{userId}/count")
    public ResponseEntity<?> countActiveAccounts(@PathVariable Long userId) {
        log.info("Counting active accounts for userId: {}", userId);
        
        long count = whatsappAccountService.countActiveAccounts(userId);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Active account count fetched successfully",
                count));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<?> activateAccount(@PathVariable Long id) {
        log.info("Activating WhatsApp account: {}", id);
        
        WhatsappAccount account = whatsappAccountService.activateAccount(id);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account activated successfully",
                account));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<?> deactivateAccount(@PathVariable Long id) {
        log.info("Deactivating WhatsApp account: {}", id);
        
        WhatsappAccount account = whatsappAccountService.deactivateAccount(id);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account deactivated successfully",
                account));
    }

    @PutMapping("/{id}/ban")
    public ResponseEntity<?> banAccount(@PathVariable Long id) {
        log.info("Banning WhatsApp account: {}", id);
        
        WhatsappAccount account = whatsappAccountService.banAccount(id);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account banned successfully",
                account));
    }

    @PutMapping("/status")
    public ResponseEntity<?> updateStatus(@Valid @RequestBody UpdateStatusRequest request) {
        log.info("Updating status for account: {}", request.getAccountId());
        
        WhatsappAccount account = whatsappAccountService.updateStatus(
                request.getAccountId(),
                request.getStatus());
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Account status updated successfully",
                account));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable Long id) {
        log.info("Deleting WhatsApp account: {}", id);
        
        whatsappAccountService.deleteAccount(id);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "WhatsApp account deleted successfully",
                null));
    }

    @Data
    public static class UpdateStatusRequest {
        private Long accountId;
        private String status;
    }
}