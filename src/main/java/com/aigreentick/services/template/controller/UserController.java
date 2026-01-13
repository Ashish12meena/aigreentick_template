package com.aigreentick.services.template.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aigreentick.services.template.dto.response.ResponseMessage;
import com.aigreentick.services.template.enums.ResponseStatus;
import com.aigreentick.services.template.model.User;
import com.aigreentick.services.template.service.impl.other.UserServiceImpl;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    private final UserServiceImpl userService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        log.info("Fetching user by ID: {}", id);
        
        User user = userService.getUserById(id);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "User fetched successfully",
                user));
    }

    @GetMapping("/active/{id}")
    public ResponseEntity<?> getActiveUserById(@PathVariable Long id) {
        log.info("Fetching active user by ID: {}", id);
        
        User user = userService.getActiveUserById(id);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Active user fetched successfully",
                user));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<?> getUserByEmail(@PathVariable String email) {
        log.info("Fetching user by email: {}", email);
        
        User user = userService.getUserByEmail(email);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "User fetched successfully",
                user));
    }

    @GetMapping("/mobile/{mobile}")
    public ResponseEntity<?> getUserByMobile(@PathVariable String mobile) {
        log.info("Fetching user by mobile: {}", mobile);
        
        User user = userService.getUserByMobile(mobile);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "User fetched successfully",
                user));
    }

    @GetMapping("/check-balance/{userId}")
    public ResponseEntity<?> checkBalance(
            @PathVariable Long userId,
            @RequestBody BalanceCheckRequest request) {
        
        log.info("Checking balance for userId: {}", userId);
        
        boolean hasSufficient = userService.hasSufficientBalance(userId, request.getAmount());
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                hasSufficient ? "Sufficient balance" : "Insufficient balance",
                hasSufficient));
    }

    @PostMapping
    public ResponseEntity<?> createUser(@Valid @RequestBody User user) {
        log.info("Creating user: {}", user.getEmail());
        
        User savedUser = userService.save(user);
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "User created successfully",
                savedUser));
    }

    @PutMapping("/balance")
    public ResponseEntity<?> updateBalance(@Valid @RequestBody UpdateBalanceRequest request) {
        log.info("Updating balance for userId: {}", request.getUserId());
        
        User user = userService.updateBalance(request.getUserId(), request.getNewBalance());
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Balance updated successfully",
                user));
    }

    @PutMapping("/balance/deduct")
    public ResponseEntity<?> deductBalance(@Valid @RequestBody BalanceTransactionRequest request) {
        log.info("Deducting {} from userId: {}", request.getAmount(), request.getUserId());
        
        User user = userService.deductBalance(request.getUserId(), request.getAmount());
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Balance deducted successfully",
                user));
    }

    @PutMapping("/balance/add")
    public ResponseEntity<?> addBalance(@Valid @RequestBody BalanceTransactionRequest request) {
        log.info("Adding {} to userId: {}", request.getAmount(), request.getUserId());
        
        User user = userService.addBalance(request.getUserId(), request.getAmount());
        
        return ResponseEntity.ok(new ResponseMessage<>(
                ResponseStatus.SUCCESS.name(),
                "Balance added successfully",
                user));
    }

    @Data
    public static class UpdateBalanceRequest {
        private Long userId;
        private Double newBalance;
    }

    @Data
    public static class BalanceTransactionRequest {
        private Long userId;
        private Double amount;
    }

    @Data
    public static class BalanceCheckRequest {
        private Double amount;
    }
}