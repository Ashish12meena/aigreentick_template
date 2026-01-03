package com.aigreentick.services.template.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.model.WhatsappAccount;
import com.aigreentick.services.template.repository.WhatsappAccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappAccountServiceImpl {
    private final WhatsappAccountRepository whatsappAccountRepository;

    /**
     * Get WhatsApp account by ID
     */
    public WhatsappAccount getAccountById(Long id) {
        log.debug("Fetching WhatsApp account by ID: {}", id);
        return whatsappAccountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("WhatsApp account not found with ID: " + id));
    }

    /**
     * Get active WhatsApp account for user
     */
    public WhatsappAccount getActiveAccountByUserId(Long userId) {
        log.debug("Fetching active WhatsApp account for userId: {}", userId);
        return whatsappAccountRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active WhatsApp account found for userId: " + userId));
    }

    /**
     * Get all WhatsApp accounts for user
     */
    public List<WhatsappAccount> getAccountsByUserId(Long userId) {
        log.debug("Fetching all WhatsApp accounts for userId: {}", userId);
        return whatsappAccountRepository.findByUserIdAndDeletedAtIsNull(userId);
    }

    /**
     * Get account by WABA ID
     */
    public WhatsappAccount getAccountByWabaId(String wabaId) {
        log.debug("Fetching WhatsApp account by WABA ID: {}", wabaId);
        return whatsappAccountRepository.findByWhatsappBizIdAndDeletedAtIsNull(wabaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WhatsApp account not found with WABA ID: " + wabaId));
    }

    /**
     * Get account by WhatsApp number ID
     */
    public WhatsappAccount getAccountByNumberId(String numberId) {
        log.debug("Fetching WhatsApp account by number ID: {}", numberId);
        return whatsappAccountRepository.findByWhatsappNoIdAndDeletedAtIsNull(numberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WhatsApp account not found with number ID: " + numberId));
    }

    /**
     * Get account by WhatsApp number
     */
    public WhatsappAccount getAccountByNumber(String number) {
        log.debug("Fetching WhatsApp account by number: {}", number);
        return whatsappAccountRepository.findByWhatsappNoAndDeletedAtIsNull(number)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WhatsApp account not found with number: " + number));
    }

    /**
     * Check if user has active WhatsApp account
     */
    public boolean hasActiveAccount(Long userId) {
        return whatsappAccountRepository.hasActiveAccount(userId);
    }

    /**
     * Get all active WhatsApp accounts
     */
    public List<WhatsappAccount> getAllActiveAccounts() {
        log.debug("Fetching all active WhatsApp accounts");
        return whatsappAccountRepository.findAllActive();
    }

    /**
     * Count active accounts for user
     */
    public long countActiveAccounts(Long userId) {
        return whatsappAccountRepository.countActiveByUserId(userId);
    }

    /**
     * Save WhatsApp account
     */
    @Transactional
    public WhatsappAccount save(WhatsappAccount account) {
        log.debug("Saving WhatsApp account for userId: {}", account.getUserId());
        
        if (account.getId() == null) {
            account.setCreatedAt(LocalDateTime.now());
        }
        account.setUpdatedAt(LocalDateTime.now());
        
        return whatsappAccountRepository.save(account);
    }

    /**
     * Update account status
     */
    @Transactional
    public WhatsappAccount updateStatus(Long accountId, String status) {
        log.info("Updating WhatsApp account status for ID: {} to {}", accountId, status);
        
        WhatsappAccount account = getAccountById(accountId);
        account.setStatus(status);
        account.setUpdatedAt(LocalDateTime.now());
        
        return whatsappAccountRepository.save(account);
    }

    /**
     * Activate account
     */
    @Transactional
    public WhatsappAccount activateAccount(Long accountId) {
        return updateStatus(accountId, "1");
    }

    /**
     * Deactivate account
     */
    @Transactional
    public WhatsappAccount deactivateAccount(Long accountId) {
        return updateStatus(accountId, "0");
    }

    /**
     * Ban account
     */
    @Transactional
    public WhatsappAccount banAccount(Long accountId) {
        return updateStatus(accountId, "2");
    }

    /**
     * Soft delete account
     */
    @Transactional
    public void deleteAccount(Long accountId) {
        log.info("Soft deleting WhatsApp account with ID: {}", accountId);
        
        WhatsappAccount account = getAccountById(accountId);
        account.setDeletedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        
        whatsappAccountRepository.save(account);
    }
}