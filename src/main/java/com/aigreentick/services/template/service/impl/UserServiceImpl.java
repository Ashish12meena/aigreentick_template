package com.aigreentick.services.template.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.model.User;
import com.aigreentick.services.template.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl {
    private final UserRepository userRepository;

    /**
     * Get user by ID
     */
    public User getUserById(Long id) {
        log.debug("Fetching user by ID: {}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + id));
    }

    /**
     * Get active user by ID
     */
    public User getActiveUserById(Long id) {
        log.debug("Fetching active user by ID: {}", id);
        return userRepository.findActiveById(id)
                .orElseThrow(() -> new IllegalArgumentException("Active user not found with ID: " + id));
    }

    /**
     * Get user by email
     */
    public User getUserByEmail(String email) {
        log.debug("Fetching user by email: {}", email);
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
    }

    /**
     * Get user by mobile
     */
    public User getUserByMobile(String mobile) {
        log.debug("Fetching user by mobile: {}", mobile);
        return userRepository.findByMobileAndDeletedAtIsNull(mobile)
                .orElseThrow(() -> new IllegalArgumentException("User not found with mobile: " + mobile));
    }

    /**
     * Get user by API token
     */
    public User getUserByApiToken(String apiToken) {
        log.debug("Fetching user by API token");
        return userRepository.findByApiTokenAndDeletedAtIsNull(apiToken)
                .orElseThrow(() -> new IllegalArgumentException("User not found with provided API token"));
    }

    /**
     * Check if user has sufficient balance
     */
    public boolean hasSufficientBalance(Long userId, Double amount) {
        return userRepository.hasSufficientBalance(userId, amount);
    }

    /**
     * Check if email exists
     */
    public boolean emailExists(String email) {
        return userRepository.existsByEmailAndDeletedAtIsNull(email);
    }

    /**
     * Check if mobile exists
     */
    public boolean mobileExists(String mobile) {
        return userRepository.existsByMobileAndDeletedAtIsNull(mobile);
    }

    /**
     * Update user balance
     */
    @Transactional
    public User updateBalance(Long userId, Double newBalance) {
        log.info("Updating balance for userId: {} to {}", userId, newBalance);
        
        User user = getUserById(userId);
        user.setBalance(newBalance);
        user.setUpdatedAt(LocalDateTime.now());
        
        return userRepository.save(user);
    }

    /**
     * Deduct amount from user balance
     */
    @Transactional
    public User deductBalance(Long userId, Double amount) {
        log.info("Deducting {} from userId: {}", amount, userId);
        
        User user = getUserById(userId);
        
        if (!hasSufficientBalance(userId, amount)) {
            throw new IllegalStateException("Insufficient balance for userId: " + userId);
        }
        
        double newBalance = user.getBalance() - amount;
        user.setBalance(newBalance);
        user.setDebit(user.getDebit() + amount);
        user.setUpdatedAt(LocalDateTime.now());
        
        return userRepository.save(user);
    }

    /**
     * Add amount to user balance
     */
    @Transactional
    public User addBalance(Long userId, Double amount) {
        log.info("Adding {} to userId: {}", amount, userId);
        
        User user = getUserById(userId);
        double newBalance = user.getBalance() + amount;
        
        user.setBalance(newBalance);
        user.setCredit(user.getCredit() + amount);
        user.setUpdatedAt(LocalDateTime.now());
        
        return userRepository.save(user);
    }

    /**
     * Save user
     */
    @Transactional
    public User save(User user) {
        log.debug("Saving user: {}", user.getEmail());
        return userRepository.save(user);
    }
}