
// ============================================================================
// 1. OPTIMIZED NORMAL BROADCAST SERVICE
// ============================================================================

package com.aigreentick.services.template.service.impl.template;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.dto.build.MessageRequest;
import com.aigreentick.services.template.dto.build.TemplateDto;
import com.aigreentick.services.template.dto.request.WhatsappAccountInfoDto;
import com.aigreentick.services.template.dto.request.template.BroadcastDispatchItemDto;
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
import com.aigreentick.services.template.service.impl.common.WalletServiceImpl;
import com.aigreentick.services.template.service.impl.contact.BlacklistServiceImpl;
import com.aigreentick.services.template.service.impl.contact.ChatContactServiceImpl;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final TemplateBuilderServiceImpl templateBuilder;
    private final AsyncBatchDispatcherService asyncDispatchService;
    private final ObjectMapper objectMapper;

    @Value("${broadcast.batch-size:200}")
    private int batchSize;

    @Value("${broadcast.build-batch-size:500}")
    private int buildBatchSize;

    @Transactional
    public TemplateResponseDto broadcastTemplate(SendTemplateRequestDto request, Long userId) {
        log.info("=== Starting optimized broadcast for userId: {} ===", userId);

        // ========== STEP 1: Get User & WhatsApp Configuration ==========
        User user = userService.getActiveUserById(userId);
        WhatsappAccount config = whatsappAccountService.getActiveAccountByUserId(user.getId());

        // ========== STEP 2: Get and Map Template ==========
        Template template = templateService.getTemplateById(request.getTemlateId());
        TemplateDto templateDto = templateMapper.toTemplateDto(template);

        // ========== STEP 3: Calculate Pricing ==========
        BigDecimal pricePerMessage = getPricePerMessage(userId, template.getCategory(), user);

        // ========== STEP 4: Filter Blacklisted Numbers ==========
        List<String> validNumbers = blacklistService.filterBlockedNumbers(userId, request.getMobileNumbers());
        log.info("Filtered numbers: {} valid out of {} total", validNumbers.size(), request.getMobileNumbers().size());

        if (validNumbers.isEmpty()) {
            throw new IllegalArgumentException("No valid numbers after blacklist filtering");
        }

        // ========== STEP 5: Validate Wallet Balance ==========
        BigDecimal totalDeduction = pricePerMessage.multiply(new BigDecimal(validNumbers.size()));
        if (BigDecimal.valueOf(user.getBalance()).compareTo(totalDeduction) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Required: %.2f, Available: %.2f", totalDeduction,
                            user.getBalance()),
                    402);
        }

        // ========== STEP 6: Create Broadcast Record ==========
        Broadcast broadcast = createBroadcastRecord(request, user, config, validNumbers, template);

        // ========== STEP 7: Deduct Wallet Balance ==========
        deductWalletBalance(user, totalDeduction, broadcast.getId());

        // ========== STEP 8: Create Reports (Batched) ==========
        log.info("Creating reports at: {}", LocalDateTime.now());
        createReportsInBatches(user.getId(), broadcast.getId(), validNumbers);

        // ========== STEP 9: Create Chat Contacts (Batched) ==========
        log.info("Creating chat contacts at: {}", LocalDateTime.now());
        createChatContactsInBatches(user.getId(), validNumbers, request.getCountryId());

        // ========== STEP 10: BUILD ALL TEMPLATES (Synchronous, Optimized) ==========
        log.info("=== PHASE 1: Building templates for {} numbers ===", validNumbers.size());
        long buildStart = System.currentTimeMillis();

        List<BroadcastDispatchItemDto> allDispatchItems = buildAllDispatchItemsInBatches(
                userId, validNumbers, templateDto, request, broadcast.getId());

        long buildDuration = System.currentTimeMillis() - buildStart;
        log.info("=== Built {} dispatch items in {}ms ===", allDispatchItems.size(), buildDuration);

        // ========== STEP 11: DISPATCH ASYNC (Fire-and-forget) ==========
        WhatsappAccountInfoDto accountInfo = WhatsappAccountInfoDto.builder()
                .phoneNumberId(config.getWhatsappNoId())
                .accessToken(config.getParmenentToken())
                .build();

        log.info("=== PHASE 2: Starting async dispatch for {} items ===", allDispatchItems.size());

        CompletableFuture<Void> dispatchFuture = asyncDispatchService.dispatchAsync(
                allDispatchItems, accountInfo, broadcast.getId());

        // Optional: Add completion handler
        dispatchFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Async dispatch failed for broadcastId: {}", broadcast.getId(), throwable);
            } else {
                log.info("=== Async dispatch completed for broadcastId: {} ===", broadcast.getId());
            }
        });

        log.info("=== Broadcast initiated - templates built, dispatching in background ===");

        return TemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .status("BROADCAST_INITIATED")
                .build();
    }

    // ==================== PHASE 1: BUILD ALL TEMPLATES ====================

    /**
     * Build all dispatch items in batches to manage memory.
     * Builds MessageRequest -> Serializes to JSON -> Discards MessageRequest.
     * Only keeps lightweight DispatchItem (mobile + JSON string).
     */
    private List<BroadcastDispatchItemDto> buildAllDispatchItemsInBatches(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto templateDto,
            SendTemplateRequestDto request,
            Long broadcastId) {

        List<BroadcastDispatchItemDto> allItems = new ArrayList<>(phoneNumbers.size());
        int totalBatches = (phoneNumbers.size() + buildBatchSize - 1) / buildBatchSize;

        log.info("Building templates in {} batches of {} numbers", totalBatches, buildBatchSize);

        for (int i = 0; i < phoneNumbers.size(); i += buildBatchSize) {
            int end = Math.min(i + buildBatchSize, phoneNumbers.size());
            List<String> batch = phoneNumbers.subList(i, end);
            int batchNum = (i / buildBatchSize) + 1;

            log.debug("Building batch {}/{} ({} numbers)", batchNum, totalBatches, batch.size());

            // Build templates for this batch
            List<MessageRequest> messageRequests = templateBuilder.buildSendableTemplates(
                    userId, batch, templateDto, request);

            // Immediately serialize and convert to lightweight dispatch items
            for (MessageRequest msg : messageRequests) {
                try {
                    String payload = objectMapper
                            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                            .writeValueAsString(msg);

                    allItems.add(BroadcastDispatchItemDto.builder()
                            .broadcastId(broadcastId)
                            .mobileNo(msg.getTo())
                            .payload(payload)
                            .build());

                } catch (Exception e) {
                    log.error("Failed to serialize message for {}: {}", msg.getTo(), e.getMessage());
                    // Continue with other messages
                }
            }

            // MessageRequest objects are now garbage collected
            log.debug("Batch {}/{} completed - {} items serialized", batchNum, totalBatches, batch.size());
        }

        log.info("Successfully built {} dispatch items", allItems.size());
        return allItems;
    }

    // ==================== HELPER METHODS (unchanged) ====================

    private BigDecimal getPricePerMessage(Long userId, String templateCategory, User user) {
        Double charge = switch (TemplateCategory.valueOf(templateCategory)) {
            case AUTHENTICATION -> user.getAuthMsgCharge();
            case UTILITY -> user.getUtiltyMsgCharge();
            case MARKETING -> user.getMarketMsgCharge();
        };

        if (charge == null || charge <= 0) {
            throw new IllegalStateException(
                    "Charge not configured for userId=" + userId + ", category=" + templateCategory);
        }
        return BigDecimal.valueOf(charge);
    }

    private Broadcast createBroadcastRecord(SendTemplateRequestDto request, User user,
            WhatsappAccount config, List<String> validNumbers, Template template) {

        Map<String, Object> data = new HashMap<>();
        data.put("template_name", template.getName());
        data.put("language_code", request.getLanguageCode());
        data.put("is_media", request.getIsMedia());

        return broadcastService.save(Broadcast.builder()
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
                .build());
    }

    private void deductWalletBalance(User user, BigDecimal totalDeduction, Long broadcastId) {
        userService.deductBalance(user.getId(), totalDeduction.doubleValue());

        walletService.save(Wallet.builder()
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
                .build());
    }

    private void createReportsInBatches(Long userId, Long broadcastId, List<String> validNumbers) {
        for (int i = 0; i < validNumbers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, validNumbers.size());
            List<String> batch = validNumbers.subList(i, end);

            List<Report> reports = batch.stream()
                    .map(mobile -> Report.builder()
                            .userId(userId)
                            .broadcastId(broadcastId)
                            .mobile(mobile)
                            .type("template")
                            .status("pending")
                            .messageStatus("pending")
                            .platform(Platform.web)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build())
                    .toList();

            reportService.saveAll(reports);
        }
    }

    private void createChatContactsInBatches(Long userId, List<String> mobileNumbers, Long countryId) {
        for (int i = 0; i < mobileNumbers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, mobileNumbers.size());
            chatContactService.ensureContactsExist(userId, mobileNumbers.subList(i, end), countryId);
        }
    }
}