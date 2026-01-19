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
import com.aigreentick.services.template.dto.request.template.csv.SendTemplateByCsvRequestDto;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class SendTemplateByCSVOrchestratorServiceImpl {
    private final WhatsappAccountServiceImpl whatsappAccountService;
    private final TemplateServiceImpl templateService;
    private final TemplateMapper templateMapper;
    private final UserServiceImpl userService;
    private final BlacklistServiceImpl blacklistService;
    private final BroadcastServiceImpl broadcastService;
    private final ReportServiceImpl reportService;
    private final WalletServiceImpl walletService;
    private final ChatContactServiceImpl chatContactService;
    private final TemplateBuilderForCsvServiceImpl csvTemplateBuilder;
    private final AsyncBatchDispatcherService asyncDispatchService;
    private final ObjectMapper objectMapper;
    private final ContactMessagesServiceImpl contactMessagesService;

    @Value("${broadcast.batch-size:200}")
    private int batchSize;

    @Value("${broadcast.build-batch-size:500}")
    private int buildBatchSize;

    @Transactional
    public TemplateResponseDto broadcastTemplate(SendTemplateByCsvRequestDto request, Long userId) {
        log.info("=== Starting optimized CSV broadcast for userId: {} ===", userId);

        // ========== STEP 1: Get User & WhatsApp Configuration ==========
        User user = userService.getActiveUserById(userId);
        WhatsappAccount config = whatsappAccountService.getActiveAccountByUserId(user.getId());

        // ========== STEP 2: Get and Map Template ==========
        Template template = templateService.getTemplateById(Long.valueOf(request.getTemplateId()));
        TemplateDto templateDto = templateMapper.toTemplateDto(template);

        // ========== STEP 3: Calculate Pricing ==========
        BigDecimal pricePerMessage = getPricePerMessage(userId, template.getCategory(), user);

        // ========== STEP 4: Convert mobile numbers to strings & filter blacklist
        // ==========
        List<String> mobileStrings = request.getMobileNumbers().stream()
                .map(String::valueOf)
                .toList();

        List<String> validNumbers = blacklistService.filterBlockedNumbers(userId, mobileStrings);
        log.info("Filtered: {} valid out of {} total", validNumbers.size(), mobileStrings.size());

        if (validNumbers.isEmpty()) {
            throw new IllegalArgumentException("No valid numbers after blacklist filtering");
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
        Broadcast broadcast = createBroadcastRecord(request, user, validNumbers, template);

        // ========== STEP 7: Deduct Wallet Balance ==========
        deductWalletBalance(user, totalDeduction, broadcast.getId());

        // ========== STEP 8: Create Reports & Capture IDs ==========
        log.info("Creating reports at: {}", LocalDateTime.now());
        Map<String, Long> mobileToReportId = createReportsAndGetIds(user.getId(), broadcast.getId(), validNumbers);

        // ========== STEP 9: Create Chat Contacts & Capture IDs ==========
        log.info("Creating chat contacts at: {}", LocalDateTime.now());
        Long countryId = request.getCountryId() != null ? request.getCountryId().longValue() : null;
        Map<String, Long> mobileToContactId = createChatContactsAndGetIds(user.getId(), validNumbers, countryId);

        // ========== STEP 10: Create ContactMessages (Async Background) ==========
        contactMessagesService.createContactMessagesAsync(mobileToReportId, mobileToContactId);

        // ========== STEP 10: BUILD ALL TEMPLATES (Synchronous, Optimized) ==========
        log.info("=== PHASE 1: Building CSV templates for {} numbers ===", validNumbers.size());
        long buildStart = System.currentTimeMillis();

        List<BroadcastDispatchItemDto> allDispatchItems = buildAllCsvDispatchItemsInBatches(
                userId, validNumbers, templateDto, request, broadcast.getId());

        long buildDuration = System.currentTimeMillis() - buildStart;
        log.info("=== Built {} CSV dispatch items in {}ms ===", allDispatchItems.size(), buildDuration);

        // ========== STEP 11: DISPATCH ASYNC (Fire-and-forget) ==========
        WhatsappAccountInfoDto accountInfo = WhatsappAccountInfoDto.builder()
                .phoneNumberId(config.getWhatsappNoId())
                .accessToken(config.getParmenentToken())
                .build();

        log.info("=== PHASE 2: Starting async dispatch for {} CSV items ===", allDispatchItems.size());

        CompletableFuture<Void> dispatchFuture = asyncDispatchService.dispatchAsync(
                allDispatchItems, accountInfo, broadcast.getId());

        // Optional: Add completion handler
        dispatchFuture.whenComplete((result, throwable) -> {
            if (throwable != null) {
                log.error("Async CSV dispatch failed for broadcastId: {}", broadcast.getId(), throwable);
            } else {
                log.info("=== Async CSV dispatch completed for broadcastId: {} ===", broadcast.getId());
            }
        });

        log.info("=== CSV Broadcast initiated - templates built, dispatching in background ===");

        return TemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .status("BROADCAST_INITIATED")
                .build();
    }

    // ==================== PHASE 1: BUILD ALL CSV TEMPLATES ====================

    /**
     * Build all CSV dispatch items in batches to manage memory.
     * Builds MessageRequest -> Serializes to JSON -> Discards MessageRequest.
     * Only keeps lightweight DispatchItem (mobile + JSON string).
     */
    private List<BroadcastDispatchItemDto> buildAllCsvDispatchItemsInBatches(
            Long userId,
            List<String> phoneNumbers,
            TemplateDto templateDto,
            SendTemplateByCsvRequestDto request,
            Long broadcastId) {

        List<BroadcastDispatchItemDto> allItems = new ArrayList<>(phoneNumbers.size());
        int totalBatches = (phoneNumbers.size() + buildBatchSize - 1) / buildBatchSize;

        log.info("Building CSV templates in {} batches of {} numbers", totalBatches, buildBatchSize);

        for (int i = 0; i < phoneNumbers.size(); i += buildBatchSize) {
            int end = Math.min(i + buildBatchSize, phoneNumbers.size());
            List<String> batch = phoneNumbers.subList(i, end);
            int batchNum = (i / buildBatchSize) + 1;

            log.debug("Building CSV batch {}/{} ({} numbers)", batchNum, totalBatches, batch.size());

            // Build CSV templates for this batch
            List<MessageRequest> messageRequests = csvTemplateBuilder.buildSendableTemplatesFromCsv(
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
                    log.error("Failed to serialize CSV message for {}: {}", msg.getTo(), e.getMessage());
                    // Continue with other messages
                }
            }

            // MessageRequest objects are now garbage collected
            log.debug("CSV batch {}/{} completed - {} items serialized", batchNum, totalBatches, batch.size());
        }

        log.info("Successfully built {} CSV dispatch items", allItems.size());
        return allItems;
    }

    // ==================== HELPER METHODS ====================

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

    private Broadcast createBroadcastRecord(SendTemplateByCsvRequestDto request, User user,
            List<String> validNumbers, Template template) {

        log.info("Creating CSV broadcast record for {} numbers", validNumbers.size());

        Map<String, Object> data = new HashMap<>();
        data.put("template_name", template.getName());
        data.put("language_code", template.getLanguage());
        data.put("is_media", request.getIsMedia());
        data.put("source", "CSV");

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
                .source("CSV")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return broadcastService.save(broadcast);
    }

    private void deductWalletBalance(User user, BigDecimal totalDeduction, Long broadcastId) {
        log.info("Deducting {} from userId: {} for CSV broadcastId: {}", totalDeduction, user.getId(), broadcastId);

        userService.deductBalance(user.getId(), totalDeduction.doubleValue());

        Wallet wallet = Wallet.builder()
                .userId(user.getId())
                .createdBy(user.getId())
                .amount(totalDeduction.doubleValue())
                .type(Wallet.WalletType.debit)
                .status("1")
                .description("CSV Broadcast message charges")
                .transection("BROADCAST_CSV_" + broadcastId)
                .broadcastId(broadcastId.intValue())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        walletService.save(wallet);
    }

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