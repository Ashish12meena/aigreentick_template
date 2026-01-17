package com.aigreentick.services.template.service.impl.template;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.client.adapter.MessagingClientImpl;
import com.aigreentick.services.template.dto.build.MessageRequest;
import com.aigreentick.services.template.dto.build.TemplateDto;
import com.aigreentick.services.template.dto.request.WhatsappAccountInfoDto;
import com.aigreentick.services.template.dto.request.template.BroadcastDispatchItemDto;
import com.aigreentick.services.template.dto.request.template.DispatchRequestDto;
import com.aigreentick.services.template.dto.request.template.csv.SendTemplateByCsvRequestDto;
import com.aigreentick.services.template.dto.response.broadcast.BroadcastDispatchResponseDto;
import com.aigreentick.services.template.dto.response.common.FacebookApiResponse;
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
    private final MessagingClientImpl messagingClient;
    private final ObjectMapper objectMapper;

    @Value("${broadcast.batch-size:200}")
    private int batchSize;

    @Value("${broadcast.dispatch-chunk-size:100}")
    private int dispatchChunkSize;

    @Transactional
    public TemplateResponseDto broadcastTemplate(SendTemplateByCsvRequestDto request, Long userId) {
        log.info("=== Starting CSV broadcast for userId: {} ===", userId);

        // ========== STEP 1: Get User & WhatsApp Configuration ==========
        User user = userService.getActiveUserById(userId);
        WhatsappAccount config = whatsappAccountService.getActiveAccountByUserId(user.getId());

        // ========== STEP 2: Get and Map Template ==========
        Template template = templateService.getTemplateById(Long.valueOf(request.getTemplateId()));
        TemplateDto templateDto = templateMapper.toTemplateDto(template);

        // ========== STEP 3: Calculate Pricing ==========
        BigDecimal pricePerMessage = getPricePerMessage(userId, template.getCategory(), user);

        // ========== STEP 4: Convert mobile numbers to strings & filter blacklist ==========
        List<String> mobileStrings = request.getMobileNumbers().stream()
                .map(String::valueOf)
                .collect(Collectors.toList());

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
                            totalDeduction, user.getBalance()), 402);
        }

        // ========== STEP 6: Create Broadcast Record ==========
        Broadcast broadcast = createBroadcastRecord(request, user, validNumbers, template);

        // ========== STEP 7: Deduct Wallet Balance ==========
        deductWalletBalance(user, totalDeduction, broadcast.getId());

        // ========== STEP 8: Create Reports ==========
        log.info("Creating reports at: {}", LocalDateTime.now());
        createReportsInBatches(user.getId(), broadcast.getId(), validNumbers);

        // ========== STEP 9: Create Chat Contacts ==========
        log.info("Creating chat contacts at: {}", LocalDateTime.now());
        Long countryId = request.getCountryId() != null ? request.getCountryId().longValue() : null;
        createChatContactsInBatches(user.getId(), validNumbers, countryId);

        // ========== STEP 10: Build & Dispatch Messages ASYNCHRONOUSLY ==========
        log.info("Starting async dispatch at: {}", LocalDateTime.now());

        WhatsappAccountInfoDto accountInfo = WhatsappAccountInfoDto.builder()
                .phoneNumberId(config.getWhatsappNoId())
                .accessToken(config.getParmenentToken())
                .build();

        // Fire async dispatch
        CompletableFuture.runAsync(() -> {
            try {
                dispatchMessagesInChunks(userId, validNumbers, templateDto, request, 
                        broadcast.getId(), accountInfo);
            } catch (Exception e) {
                log.error("Async dispatch failed for broadcastId: {}", broadcast.getId(), e);
            }
        }).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Dispatch completed with error for broadcastId: {}", broadcast.getId(), ex);
            } else {
                log.info("Dispatch completed for broadcastId: {}", broadcast.getId());
            }
        });

        log.info("=== CSV Broadcast initiated - dispatching in background ===");

        return TemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .status("BROADCAST_INITIATED")
                .build();
    }

    /**
     * Dispatch messages in chunks using CSV template builder
     */
    private void dispatchMessagesInChunks(
            Long userId,
            List<String> validNumbers,
            TemplateDto templateDto,
            SendTemplateByCsvRequestDto request,
            Long broadcastId,
            WhatsappAccountInfoDto accountInfo) {

        log.info("Dispatching {} messages in chunks of {}", validNumbers.size(), dispatchChunkSize);

        int totalChunks = (validNumbers.size() + dispatchChunkSize - 1) / dispatchChunkSize;
        int totalDispatched = 0;
        int totalFailed = 0;

        for (int i = 0; i < validNumbers.size(); i += dispatchChunkSize) {
            int end = Math.min(i + dispatchChunkSize, validNumbers.size());
            List<String> chunk = validNumbers.subList(i, end);
            int chunkNum = (i / dispatchChunkSize) + 1;

            log.info("Processing chunk {}/{} with {} numbers", chunkNum, totalChunks, chunk.size());

            try {
                // Build messages using CSV template builder
                List<MessageRequest> messages = csvTemplateBuilder.buildSendableTemplatesFromCsv(
                        userId, chunk, templateDto, request);

                // Convert to dispatch items
                List<BroadcastDispatchItemDto> items = messages.stream()
                        .map(msg -> toDispatchItem(msg, broadcastId))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                if (items.isEmpty()) {
                    log.warn("Chunk {}/{} produced no dispatch items", chunkNum, totalChunks);
                    totalFailed += chunk.size();
                    continue;
                }

                // Dispatch to messaging service
                DispatchRequestDto dispatchRequest = DispatchRequestDto.builder()
                        .items(items)
                        .accountInfo(accountInfo)
                        .build();

                FacebookApiResponse<BroadcastDispatchResponseDto> response = 
                        messagingClient.dispatchMessage(dispatchRequest);

                if (response.isSuccess() && response.getData() != null 
                        && response.getData().getData() != null) {
                    int dispatched = response.getData().getData().getTotalDispatched();
                    int failed = response.getData().getData().getFailedCount();
                    totalDispatched += dispatched;
                    totalFailed += failed;
                    log.info("Chunk {}/{} - Dispatched: {}, Failed: {}", 
                            chunkNum, totalChunks, dispatched, failed);
                } else {
                    totalFailed += items.size();
                    log.error("Chunk {}/{} failed: {}", chunkNum, totalChunks, response.getErrorMessage());
                }

            } catch (Exception e) {
                totalFailed += chunk.size();
                log.error("Chunk {}/{} threw exception", chunkNum, totalChunks, e);
            }
        }

        log.info("=== Dispatch completed - Total Dispatched: {}, Total Failed: {} ===", 
                totalDispatched, totalFailed);
    }

    private BroadcastDispatchItemDto toDispatchItem(MessageRequest msg, Long broadcastId) {
        try {
            String payload = objectMapper
                    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                    .writeValueAsString(msg);

            return BroadcastDispatchItemDto.builder()
                    .broadcastId(broadcastId)
                    .mobileNo(msg.getTo())
                    .payload(payload)
                    .build();
        } catch (Exception e) {
            log.error("Failed to serialize message for {}: {}", msg.getTo(), e.getMessage());
            return null;
        }
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

        log.info("Creating broadcast record for {} numbers", validNumbers.size());

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
        log.info("Deducting {} from userId: {} for broadcastId: {}", totalDeduction, user.getId(), broadcastId);

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

    private void createReportsInBatches(Long userId, Long broadcastId, List<String> numbers) {
        log.info("Creating reports for {} numbers in batches of {}", numbers.size(), batchSize);

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

            reportService.saveAll(reports);
        }
        log.info("Created {} reports", numbers.size());
    }

    private void createChatContactsInBatches(Long userId, List<String> numbers, Long countryId) {
        log.info("Creating contacts for {} numbers in batches of {}", numbers.size(), batchSize);

        for (int i = 0; i < numbers.size(); i += batchSize) {
            int end = Math.min(i + batchSize, numbers.size());
            chatContactService.ensureContactsExist(userId, numbers.subList(i, end), countryId);
        }
        log.info("Ensured {} contacts exist", numbers.size());
    }
}