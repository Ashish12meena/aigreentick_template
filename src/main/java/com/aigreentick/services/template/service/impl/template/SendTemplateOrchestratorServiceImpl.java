package com.aigreentick.services.template.service.impl.template;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import com.aigreentick.services.template.service.impl.contact.ContactMessagesServiceImpl;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the WhatsApp template broadcast workflow.
 * 
 * Flow: Validate -> Filter Blacklist -> Check Balance -> Create Broadcast 
 *       -> Deduct Wallet -> Create Reports -> Create Contacts -> Link ContactMessages 
 *       -> Build Templates -> Dispatch Async
 */
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
    private final ContactMessagesServiceImpl contactMessagesService;
    private final TemplateBuilderServiceImpl templateBuilder;
    private final AsyncBatchDispatcherService asyncDispatchService;
    private final ObjectMapper objectMapper;

    @Value("${broadcast.batch-size:200}")
    private int batchSize;

    @Value("${broadcast.build-batch-size:500}")
    private int buildBatchSize;

    /**
     * Main entry point for broadcasting WhatsApp templates.
     * This method is transactional to ensure atomicity of DB operations
     * (broadcast creation, wallet deduction, reports, contacts).
     * 
     * Note: Async dispatch happens AFTER transaction commits.
     */
    @Transactional
    public TemplateResponseDto broadcastTemplate(SendTemplateRequestDto request, Long userId) {
        log.info("=== Starting optimized broadcast for userId: {} ===", userId);

        // Step 1-3: Load required entities
        User user = userService.getUserById(userId);
        WhatsappAccount config = whatsappAccountService.getActiveAccountByUserId(user.getId());
        Template template = templateService.getTemplateById(request.getTemlateId());
        TemplateDto templateDto = templateMapper.toTemplateDto(template);
        
        // Step 4: Get price based on template category (AUTH/UTILITY/MARKETING)
        BigDecimal pricePerMessage = getPricePerMessage(userId, template.getCategory(), user);

        // Step 5: Remove blacklisted numbers
        List<String> validNumbers = blacklistService.filterBlockedNumbers(userId, request.getMobileNumbers());
        log.info("Filtered numbers: {} valid out of {} total", validNumbers.size(), request.getMobileNumbers().size());

        if (validNumbers.isEmpty()) {
            throw new IllegalArgumentException("No valid numbers after blacklist filtering");
        }

        // Step 6: Validate user has sufficient balance
        BigDecimal totalDeduction = pricePerMessage.multiply(new BigDecimal(validNumbers.size()));
        if (BigDecimal.valueOf(user.getBalance()).compareTo(totalDeduction) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Insufficient balance. Required: %.2f, Available: %.2f",
                            totalDeduction, user.getBalance()),
                    402);
        }

        // Step 7: Create broadcast record and deduct wallet (within same transaction)
        Broadcast broadcast = createBroadcastRecord(request, user, config, validNumbers, template);
        deductWalletBalance(user, totalDeduction, broadcast.getId());

        // Step 8: Create report entries for each recipient (for tracking delivery status)
        log.info("Creating reports at: {}", LocalDateTime.now());
        Map<String, Long> mobileToReportId = createReportsAndGetIds(user.getId(), broadcast.getId(), validNumbers);

        // Step 9: Ensure chat contacts exist (creates if not present, returns IDs)
        log.info("Creating chat contacts at: {}", LocalDateTime.now());
        Map<String, Long> mobileToContactId = createChatContactsAndGetIds(
                user.getId(), validNumbers, request.getCountryId());

        // Step 10: Link contacts to messages via junction table (async, non-blocking)
        contactMessagesService.createContactMessagesAsync(mobileToReportId, mobileToContactId);

        // Step 11: Build WhatsApp API payloads for all recipients
        log.info("=== PHASE 1: Building templates for {} numbers ===", validNumbers.size());
        long buildStart = System.currentTimeMillis();

        List<BroadcastDispatchItemDto> allDispatchItems = buildAllDispatchItemsInBatches(
                userId, validNumbers, templateDto, request, broadcast.getId());

        long buildDuration = System.currentTimeMillis() - buildStart;
        log.info("=== Built {} dispatch items in {}ms ===", allDispatchItems.size(), buildDuration);

        // Step 12: Dispatch messages asynchronously (returns immediately)
        WhatsappAccountInfoDto accountInfo = WhatsappAccountInfoDto.builder()
                .phoneNumberId(config.getWhatsappNoId())
                .accessToken(config.getParmenentToken())
                .build();

        log.info("=== PHASE 2: Starting async dispatch for {} items ===", allDispatchItems.size());

        CompletableFuture<Void> dispatchFuture = asyncDispatchService.dispatchAsync(
                allDispatchItems, accountInfo, broadcast.getId());

        // Log completion (non-blocking callback)
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

    /**
     * Creates report entries in batches and returns mobile -> reportId mapping.
     * Reports track individual message delivery status.
     */
    private Map<String, Long> createReportsAndGetIds(Long userId, Long broadcastId, List<String> validNumbers) {
        Map<String, Long> mobileToReportId = new HashMap<>();

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

            List<Report> savedReports = reportService.saveAll(reports);

            for (Report saved : savedReports) {
                mobileToReportId.put(saved.getMobile(), saved.getId());
            }
        }

        log.info("Created {} reports with IDs", mobileToReportId.size());
        return mobileToReportId;
    }

    /**
     * Ensures contacts exist for all mobile numbers and returns mobile -> contactId mapping.
     * Creates new contacts if they don't exist.
     */
    private Map<String, Long> createChatContactsAndGetIds(Long userId, List<String> mobileNumbers, Long countryId) {
        Map<String, Long> mobileToContactId = new HashMap<>();

        for (int i = 0; i < mobileNumbers.size(); i += batchSize) {
            int end = Math.min(i + mobileNumbers.size(), mobileNumbers.size());
            List<String> batch = mobileNumbers.subList(i, end);

            Map<String, Long> batchResult = chatContactService.ensureContactsExistAndGetIds(
                    userId, batch, countryId);

            mobileToContactId.putAll(batchResult);
        }

        log.info("Ensured {} contacts with IDs", mobileToContactId.size());
        return mobileToContactId;
    }

    /**
     * Returns message price based on template category.
     * Prices are configured per-user in User entity.
     */
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

    /**
     * Creates the broadcast record with all metadata.
     * Stores mobile numbers as comma-separated string for reference.
     */
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
                        ? LocalDateTime.ofInstant(request.getScheduledAt(), ZoneId.systemDefault())
                        : null)
                .status("1")
                .numbers(String.join(",", validNumbers))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    /**
     * Deducts amount from user balance and creates wallet transaction record.
     */
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

    /**
     * Builds WhatsApp API payloads in batches to avoid memory pressure.
     * Each payload is serialized to JSON for Kafka dispatch.
     */
    private List<BroadcastDispatchItemDto> buildAllDispatchItemsInBatches(
            Long userId, List<String> phoneNumbers, TemplateDto templateDto,
            SendTemplateRequestDto request, Long broadcastId) {

        List<BroadcastDispatchItemDto> allItems = new ArrayList<>(phoneNumbers.size());
        int totalBatches = (phoneNumbers.size() + buildBatchSize - 1) / buildBatchSize;

        log.info("Building templates in {} batches of {} numbers", totalBatches, buildBatchSize);

        for (int i = 0; i < phoneNumbers.size(); i += buildBatchSize) {
            int end = Math.min(i + buildBatchSize, phoneNumbers.size());
            List<String> batch = phoneNumbers.subList(i, end);
            int batchNum = (i / buildBatchSize) + 1;

            log.debug("Building batch {}/{} ({} numbers)", batchNum, totalBatches, batch.size());

            List<MessageRequest> messageRequests = templateBuilder.buildSendableTemplates(
                    userId, batch, templateDto, request);

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
                }
            }

            log.debug("Batch {}/{} completed - {} items serialized", batchNum, totalBatches, batch.size());
        }

        log.info("Successfully built {} dispatch items", allItems.size());
        return allItems;
    }
}