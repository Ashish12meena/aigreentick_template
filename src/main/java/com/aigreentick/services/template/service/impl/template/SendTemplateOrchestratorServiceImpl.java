package com.aigreentick.services.template.service.impl.template;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.dto.build.TemplateDto;
import com.aigreentick.services.template.dto.request.WhatsappAccountInfoDto;
import com.aigreentick.services.template.dto.request.template.SendTemplateRequestDto;
import com.aigreentick.services.template.dto.response.template.TemplateResponseDto;
import com.aigreentick.services.template.enums.Platform;
import com.aigreentick.services.template.enums.TemplateCategory;
import com.aigreentick.services.template.exceptions.InsufficientBalanceException;
import com.aigreentick.services.template.mapper.TemplateMapper;
import com.aigreentick.services.template.model.account.User;
import com.aigreentick.services.template.model.account.WhatsappAccount;
import com.aigreentick.services.template.model.broadcast.Broadcast;
import com.aigreentick.services.template.model.broadcast.Report;
import com.aigreentick.services.template.model.common.Wallet;
import com.aigreentick.services.template.model.template.Template;
import com.aigreentick.services.template.service.impl.account.UserServiceImpl;
import com.aigreentick.services.template.service.impl.account.WhatsappAccountServiceImpl;
import com.aigreentick.services.template.service.impl.broadcast.AsyncBatchDispatcherService;
import com.aigreentick.services.template.service.impl.broadcast.BroadcastServiceImpl;
import com.aigreentick.services.template.service.impl.broadcast.ReportServiceImpl;
import com.aigreentick.services.template.service.impl.broadcast.AsyncBatchDispatcherService.DispatchSummary;
import com.aigreentick.services.template.service.impl.common.WalletServiceImpl;
import com.aigreentick.services.template.service.impl.contact.BlacklistServiceImpl;
import com.aigreentick.services.template.service.impl.contact.ChatContactServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SendTemplateOrchestratorServiceImpl {
    private final WhatsappAccountServiceImpl whatsappAccountService;
    private final TemplateServiceImpl templateService;
    private final TemplateMapper templateMapper;
    private final UserServiceImpl userService;
    private final BlacklistServiceImpl blacklistService;
    private final BroadcastServiceImpl broadcastService;
    private final ReportServiceImpl reportService;
    private final WalletServiceImpl walletService;
    private final ChatContactServiceImpl chatContactService;
    private final AsyncBatchDispatcherService asyncBatchDispatcher;

    @Value("${broadcast.batch-size:200}")
    private int batchSize;

    @Transactional
    public TemplateResponseDto broadcastTemplate(SendTemplateRequestDto request, Long userId) {
        log.info("=== Starting broadcast for userId: {} ===", userId);

        // ========== STEP 1: Get User & WhatsApp Configuration ==========
        User user = userService.getActiveUserById(userId);
        WhatsappAccount config = whatsappAccountService.getActiveAccountByUserId(user.getId());

        // ========== STEP 2: Get and Map Template ==========
        Template template = templateService.getTemplateById(request.getTemlateId());
        TemplateDto templateDto = templateMapper.toTemplateDto(template);

        // ========== STEP 3: Calculate Pricing ==========
        BigDecimal pricePerMessage = getPricePerMessage(userId, template.getCategory(), user);

        // ========== STEP 4: Filter Blacklisted Numbers ==========
        List<String> validNumbers = filterBlacklistedNumbers(userId, request.getMobileNumbers());
        log.info("Filtered numbers: {} valid out of {} total",
                validNumbers.size(), request.getMobileNumbers().size());

        if (validNumbers.isEmpty()) {
            throw new IllegalArgumentException("No valid numbers to broadcast after filtering blacklist");
        }

        // ========== STEP 5: Validate Wallet Balance ==========
        BigDecimal totalDeduction = pricePerMessage.multiply(new BigDecimal(validNumbers.size()));
        if (BigDecimal.valueOf(user.getBalance()).compareTo(totalDeduction) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Required: %.2f, Available: %.2f",
                            totalDeduction, user.getBalance()),
                    402);
        }

        // ========== STEP 6: Create Broadcast Record ==========
        Broadcast broadcast = createBroadcastRecord(request, user, config, validNumbers, template);

        // ========== STEP 7: Create Broadcast Media (if applicable) ==========
        if (Boolean.TRUE.equals(request.getIsMedia())) {
            createBroadcastMedia(broadcast, request);
        }

        // ========== STEP 8: Deduct Wallet Balance ==========
        deductWalletBalance(user, totalDeduction, broadcast.getId());

        // ========== STEP 9: Create Reports in Batches (REPORTS FIRST) ==========
        log.info("=== Creating reports at: {} ===", LocalDateTime.now());
        createReportsInBatches(user.getId(), broadcast.getId(), validNumbers);

        // ========== STEP 10: Create Chat Contacts for ALL Numbers (AFTER REPORTS) ==========
        log.info("=== Creating chat contacts at: {} ===", LocalDateTime.now());
        createChatContactsInBatches(user.getId(), validNumbers, request.getCountryId());

        // ========== STEP 11: Build Templates and Dispatch ASYNCHRONOUSLY ==========
        log.info("=== Starting async dispatch at: {} ===", LocalDateTime.now());
        
        WhatsappAccountInfoDto accountInfo = WhatsappAccountInfoDto.builder()
                .phoneNumberId(config.getWhatsappNoId())
                .accessToken(config.getParmenentToken())
                .build();

        // Fire and forget - dispatch happens asynchronously in background
        CompletableFuture<DispatchSummary> dispatchFuture = asyncBatchDispatcher.dispatchMessagesAsync(
                userId, validNumbers, templateDto, request, broadcast.getId(), accountInfo);

        // Optional: Add completion handler for logging/metrics
        dispatchFuture.whenComplete((summary, throwable) -> {
            if (throwable != null) {
                log.error("Async dispatch failed for broadcastId: {}", broadcast.getId(), throwable);
            } else {
                log.info("=== Async dispatch completed for broadcastId: {} - Dispatched: {}, Failed: {} ===",
                        broadcast.getId(), summary.dispatched(), summary.failed());
            }
        });

        log.info("=== Broadcast initiated successfully - dispatching in background ===");
        
        return TemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .status("BROADCAST_INITIATED")
                .build();
    }

    // ==================== HELPER METHODS (unchanged) ====================

    private BigDecimal getPricePerMessage(Long userId, String templateCategory, User user) {
        Double charge;

        switch (TemplateCategory.valueOf(templateCategory)) {
            case AUTHENTICATION -> charge = user.getAuthMsgCharge();
            case UTILITY -> charge = user.getUtiltyMsgCharge();
            case MARKETING -> charge = user.getMarketMsgCharge();
            default -> throw new IllegalArgumentException(
                    "Unsupported template category: " + templateCategory);
        }

        if (charge == null || charge <= 0) {
            throw new IllegalStateException(
                    "Charge not configured for userId=" + userId + ", category=" + templateCategory);
        }

        return BigDecimal.valueOf(charge);
    }

    private List<String> filterBlacklistedNumbers(Long userId, List<String> mobileNumbers) {
        return blacklistService.filterBlockedNumbers(userId, mobileNumbers);
    }

    private Broadcast createBroadcastRecord(
            SendTemplateRequestDto request,
            User user,
            WhatsappAccount config,
            List<String> validNumbers,
            Template template) {

        log.info("Creating broadcast record for {} numbers", validNumbers.size());

        Map<String, Object> data = new HashMap<>();
        data.put("template_name", template.getName());
        data.put("language_code", request.getLanguageCode());
        data.put("is_media", request.getIsMedia());
        if (request.getIsMedia() != null && request.getIsMedia()) {
            data.put("media_id", request.getMediaId());
            data.put("media_type", request.getMediaType());
        }

        Broadcast broadcast = Broadcast.builder()
                .userId(user.getId())
                .templateId(template.getId())
                .countryId(request.getCountryId())
                .campname(request.getCampanyName())
                .isMedia(request.getIsMedia() != null && request.getIsMedia() ? "1" : "0")
                .data(data)
                .total(validNumbers.size())
                .scheduleAt(request.getScheduledAt() != null
                        ? LocalDateTime.ofInstant(request.getScheduledAt(), java.time.ZoneId.systemDefault())
                        : null)
                .status("1")
                .numbers(String.join(",", validNumbers))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return broadcastService.save(broadcast);
    }

    private void createBroadcastMedia(Broadcast broadcast, SendTemplateRequestDto request) {
        log.info("Creating broadcast media record for broadcastId: {}", broadcast.getId());
        // Implementation for broadcast media if needed
    }

    private void deductWalletBalance(User user, BigDecimal totalDeduction, Long broadcastId) {
        log.info("Deducting {} from userId: {} for broadcastId: {}",
                totalDeduction, user.getId(), broadcastId);

        userService.deductBalance(user.getId(), totalDeduction.doubleValue());

        Wallet wallet = Wallet.builder()
                .userId(user.getId())
                .createdBy(user.getId())
                .amount(totalDeduction.doubleValue())
                .type(Wallet.WalletType.debit)
                .status("1")
                .description("Broadcast message charges")
                .transection("BROADCAST_" + broadcastId)
                .broadcastId(broadcastId.intValue())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        walletService.save(wallet);
    }

    private void createReportsInBatches(Long userId, Long broadcastId, List<String> validNumbers) {
        log.info("Creating reports for {} numbers in batches of {}", validNumbers.size(), batchSize);

        int totalBatches = (validNumbers.size() + batchSize - 1) / batchSize;

        for (int i = 0; i < validNumbers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, validNumbers.size());
            List<String> batch = validNumbers.subList(i, end);

            List<Report> reports = batch.stream()
                    .map(mobile -> createReport(userId, broadcastId, mobile))
                    .toList();

            reportService.saveAll(reports);

            log.debug("Saved report batch {}/{} ({} reports)",
                    (i / batchSize) + 1, totalBatches, reports.size());
        }

        log.info("Successfully created {} reports", validNumbers.size());
    }

    private void createChatContactsInBatches(Long userId, List<String> mobileNumbers, Long countryId) {
        log.info("Creating chat contacts for {} numbers in batches of {}", mobileNumbers.size(), batchSize);

        int totalBatches = (mobileNumbers.size() + batchSize - 1) / batchSize;

        for (int i = 0; i < mobileNumbers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, mobileNumbers.size());
            List<String> batch = mobileNumbers.subList(i, end);

            chatContactService.ensureContactsExist(userId, batch, countryId);

            log.debug("Created contacts batch {}/{} ({} contacts)",
                    (i / batchSize) + 1, totalBatches, batch.size());
        }

        log.info("Successfully ensured {} chat contacts exist", mobileNumbers.size());
    }

    private Report createReport(Long userId, Long broadcastId, String mobile) {
        return Report.builder()
                .userId(userId)
                .broadcastId(broadcastId)
                .mobile(mobile)
                .type("template")
                .status("pending")
                .messageStatus("pending")
                .platform(Platform.web)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}