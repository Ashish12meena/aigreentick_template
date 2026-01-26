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
import com.aigreentick.services.template.dto.request.template.normal.SendTemplateNormalRequestDto;
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
import com.aigreentick.services.template.service.impl.template.builder.TemplateBuilderForNormalServiceImpl;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates WhatsApp template broadcast for Normal flow.
 * 
 * Key difference from CSV flow:
 * - Variables come as comma-separated string (same for ALL contacts)
 * - No per-contact variable override for non-carousel
 * - Simpler variable structure
 * 
 * Flow: Validate -> Filter Blacklist -> Check Balance -> Create Broadcast 
 *       -> Deduct Wallet -> Create Reports -> Create Contacts -> Link ContactMessages 
 *       -> Build Templates -> Dispatch Async
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SendTemplateByNormalOrchestratorServiceImpl {

    private final WhatsappAccountServiceImpl whatsappAccountService;
    private final TemplateServiceImpl templateService;
    private final TemplateMapper templateMapper;
    private final UserServiceImpl userService;
    private final BlacklistServiceImpl blacklistService;
    private final BroadcastServiceImpl broadcastService;
    private final ReportServiceImpl reportService;
    private final WalletServiceImpl walletService;
    private final ChatContactServiceImpl chatContactService;
    private final TemplateBuilderForNormalServiceImpl normalTemplateBuilder;
    private final AsyncBatchDispatcherService asyncDispatchService;
    private final ObjectMapper objectMapper;
    private final ContactMessagesServiceImpl contactMessagesService;

    @Value("${broadcast.batch-size:1000}")
    private int batchSize;

    @Value("${broadcast.build-batch-size:500}")
    private int buildBatchSize;

    /**
     * Main entry point for Normal broadcast.
     * Transactional to ensure atomicity of DB operations.
     */
    @Transactional
    public TemplateResponseDto broadcastTemplate(SendTemplateNormalRequestDto request, Long userId) {
        log.info("=== Starting Normal broadcast for userId: {} ===", userId);

        // Step 1-2: Load user and WhatsApp configuration
        User user = userService.getUserById(userId);
        WhatsappAccount config = whatsappAccountService.getActiveAccountByUserId(user.getId());

        // Step 3: Load and map template
        Template template = templateService.getTemplateById(Long.valueOf(request.getTemplateId()));
        TemplateDto templateDto = templateMapper.toTemplateDto(template);

        // Step 4: Get price based on template category
        BigDecimal pricePerMessage = getPricePerMessage(userId, template.getCategory(), user);

        // Step 5: Filter blacklisted numbers (mobileNumbers already String)
        List<String> validNumbers = blacklistService.filterBlockedNumbers(userId, request.getMobileNumbers());
        log.info("Filtered: {} valid out of {} total", validNumbers.size(), request.getMobileNumbers().size());

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

        // Step 7: Create broadcast record
        Broadcast broadcast = createBroadcastRecord(request, user, validNumbers, template);

        // Step 8: Deduct wallet balance and create transaction record
        deductWalletBalance(user, totalDeduction, broadcast.getId());

        // Step 9: Create report entries for tracking delivery status
        log.info("Creating reports at: {}", LocalDateTime.now());
        Map<String, Long> mobileToReportId = createReportsAndGetIds(user.getId(), broadcast.getId(), validNumbers);

         // Step 9: Create contacts and link messages (chained async - fire and forget)
        log.info(" Starting chained async for contacts + messages ===");
        contactMessagesService.createContactsAndLinkMessagesAsync(
                mobileToReportId,
                user.getId(),
                Long.valueOf(request.getCountryId()));

        // Step 12: Build WhatsApp API payloads
        log.info("=== PHASE 1: Building Normal templates for {} numbers ===", validNumbers.size());
        long buildStart = System.currentTimeMillis();

        List<BroadcastDispatchItemDto> allDispatchItems = buildAllNormalDispatchItemsInBatches(
                userId, validNumbers, templateDto, request, broadcast.getId());

        long buildDuration = System.currentTimeMillis() - buildStart;
        log.info("=== Built {} Normal dispatch items in {}ms ===", allDispatchItems.size(), buildDuration);

        // Step 13: Dispatch messages asynchronously (returns immediately)
        WhatsappAccountInfoDto accountInfo = WhatsappAccountInfoDto.builder()
                .phoneNumberId(config.getWhatsappNoId())
                .accessToken(config.getParmenentToken())
                .build();

        log.info("=== PHASE 2: Starting async dispatch for {} Normal items ===", allDispatchItems.size());

        CompletableFuture<Void> dispatchFuture = asyncDispatchService.dispatchAsync(
                allDispatchItems, accountInfo, broadcast.getId());

        // Log completion (non-blocking callback)
        dispatchFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Async Normal dispatch failed for broadcastId: {}", broadcast.getId(), throwable);
            } else {
                log.info("=== Async Normal dispatch completed for broadcastId: {} ===", broadcast.getId());
            }
        });

        log.info("=== Normal Broadcast initiated - templates built, dispatching in background ===");

        return TemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .status("BROADCAST_INITIATED")
                .build();
    }

    /**
     * Builds WhatsApp API payloads in batches using Normal template builder.
     * Since all contacts get same variables, this is more efficient than CSV.
     */
    private List<BroadcastDispatchItemDto> buildAllNormalDispatchItemsInBatches(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto templateDto,
            SendTemplateNormalRequestDto request,
            Long broadcastId) {

        List<BroadcastDispatchItemDto> allItems = new ArrayList<>(phoneNumbers.size());
        int totalBatches = (phoneNumbers.size() + buildBatchSize - 1) / buildBatchSize;

        log.info("Building Normal templates in {} batches of {} numbers", totalBatches, buildBatchSize);

        for (int i = 0; i < phoneNumbers.size(); i += buildBatchSize) {
            int end = Math.min(i + buildBatchSize, phoneNumbers.size());
            List<String> batch = phoneNumbers.subList(i, end);
            int batchNum = (i / buildBatchSize) + 1;

            log.debug("Building Normal batch {}/{} ({} numbers)", batchNum, totalBatches, batch.size());

            // Build templates with Normal builder
            List<MessageRequest> messageRequests = normalTemplateBuilder.buildSendableTemplatesFromNormal(
                    userId, batch, templateDto, request);

            // Serialize immediately to minimize memory footprint
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
                    log.error("Failed to serialize Normal message for {}: {}", msg.getTo(), e.getMessage());
                }
            }

            log.debug("Normal batch {}/{} completed - {} items serialized", batchNum, totalBatches, batch.size());
        }

        log.info("Successfully built {} Normal dispatch items", allItems.size());
        return allItems;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Returns message price based on template category.
     */
    private BigDecimal getPricePerMessage(Long userId, String category, User user) {
        Double charge = switch (TemplateCategory.valueOf(category)) {
            case AUTHENTICATION -> user.getAuthMsgCharge();
            case UTILITY -> user.getUtiltyMsgCharge();
            case MARKETING -> user.getMarketMsgCharge();
        };

        if (charge == null || charge <= 0) {
            throw new IllegalStateException("Charge not configured for userId=" + userId + ", category=" + category);
        }
        return BigDecimal.valueOf(charge);
    }

    /**
     * Creates broadcast record with Normal-specific metadata.
     */
    private Broadcast createBroadcastRecord(
            SendTemplateNormalRequestDto request,
            User user,
            List<String> validNumbers,
            Template template) {

        log.info("Creating Normal broadcast record for {} numbers", validNumbers.size());

        Map<String, Object> data = new HashMap<>();
        data.put("template_name", template.getName());
        data.put("language_code", template.getLanguage());
        data.put("is_media", request.getIsMedia());
        data.put("source", "NORMAL");

        // Parse optional schedule date
        LocalDateTime scheduleAt = null;
        if (request.getScheduleDate() != null && !request.getScheduleDate().isBlank()) {
            try {
                scheduleAt = LocalDateTime.parse(request.getScheduleDate());
            } catch (Exception e) {
                log.warn("Could not parse schedule date: {}", request.getScheduleDate());
            }
        }

        Broadcast broadcast = Broadcast.builder()
                .userId(user.getId())
                .templateId(template.getId())
                .countryId(request.getCountryId() != null ? request.getCountryId().longValue() : null)
                .campname(request.getCampName())
                .isMedia(Boolean.TRUE.equals(request.getIsMedia()) ? "1" : "0")
                .data(data)
                .total(validNumbers.size())
                .scheduleAt(scheduleAt)
                .status("1")
                .numbers(String.join(",", validNumbers))
                .source("NORMAL")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return broadcastService.save(broadcast);
    }

    /**
     * Deducts amount from user balance and creates wallet transaction record.
     */
    private void deductWalletBalance(User user, BigDecimal totalDeduction, Long broadcastId) {
        log.info("Deducting {} from userId: {} for Normal broadcastId: {}", 
                totalDeduction, user.getId(), broadcastId);

        userService.deductBalance(user.getId(), totalDeduction.doubleValue());

        Wallet wallet = Wallet.builder()
                .userId(user.getId())
                .createdBy(user.getId())
                .amount(totalDeduction.doubleValue())
                .type(Wallet.WalletType.debit)
                .status("1")
                .description("Normal Broadcast message charges")
                .transection("BROADCAST_NORMAL_" + broadcastId)
                .broadcastId(broadcastId.intValue())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        walletService.save(wallet);
    }

    /**
     * Creates report entries in batches and returns mobile -> reportId mapping.
     */
    private Map<String, Long> createReportsAndGetIds(Long userId, Long broadcastId, List<String> numbers) {
        Map<String, Long> mobileToReportId = new HashMap<>();

        for (int i = 0; i < numbers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, numbers.size());
            List<String> batch = numbers.subList(i, end);

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
     */
    private Map<String, Long> createChatContactsAndGetIds(Long userId, List<String> numbers, Long countryId) {
        Map<String, Long> mobileToContactId = new HashMap<>();

        for (int i = 0; i < numbers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, numbers.size());
            List<String> batch = numbers.subList(i, end);

            Map<String, Long> batchResult = chatContactService.ensureContactsExistAndGetIds(userId, batch, countryId);
            mobileToContactId.putAll(batchResult);
        }

        log.info("Ensured {} contacts with IDs", mobileToContactId.size());
        return mobileToContactId;
    }
}