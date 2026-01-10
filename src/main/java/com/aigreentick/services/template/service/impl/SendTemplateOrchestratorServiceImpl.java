package com.aigreentick.services.template.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.client.adapter.MessagingClientImpl;
import com.aigreentick.services.template.dto.build.MessageRequest;
import com.aigreentick.services.template.dto.build.TemplateDto;
import com.aigreentick.services.template.dto.request.BroadcastDispatchItemDto;
import com.aigreentick.services.template.dto.request.DispatchRequestDto;
import com.aigreentick.services.template.dto.request.SendTemplateRequestDto;
import com.aigreentick.services.template.dto.request.WhatsappAccountInfoDto;
import com.aigreentick.services.template.dto.response.BroadcastDispatchResponseDto;
import com.aigreentick.services.template.dto.response.FacebookApiResponse;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.enums.Platform;
import com.aigreentick.services.template.enums.TemplateCategory;
import com.aigreentick.services.template.exceptions.InsufficientBalanceException;
import com.aigreentick.services.template.mapper.TemplateMapper;
import com.aigreentick.services.template.model.Broadcast;
import com.aigreentick.services.template.model.Report;
import com.aigreentick.services.template.model.Template;
import com.aigreentick.services.template.model.User;
import com.aigreentick.services.template.model.Wallet;
import com.aigreentick.services.template.model.WhatsappAccount;
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
    private final TemplateBuilderServiceImpl templateBuilderService;
    private final MessagingClientImpl messagingClient;
    private final ChatContactServiceImpl chatContactService;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 500;

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

        // ========== STEP 11: Build Templates and Dispatch ==========
        log.info("=== Building and dispatching messages at: {} ===", LocalDateTime.now());
        dispatchMessages(userId, validNumbers, templateDto, request, broadcast.getId(), config);

        log.info("=== Broadcast completed successfully ===");
        return TemplateResponseDto.builder()
                .id(template.getId())
                .name(template.getName())
                .status("BROADCAST_INITIATED")
                .build();
    }

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
        log.info("Creating reports for {} numbers in batches of {}", validNumbers.size(), BATCH_SIZE);

        for (int i = 0; i < validNumbers.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, validNumbers.size());
            List<String> batch = validNumbers.subList(i, end);

            List<Report> reports = batch.stream()
                    .map(mobile -> createReport(userId, broadcastId, mobile))
                    .collect(Collectors.toList());

            reportService.saveAll(reports);

            log.debug("Saved report batch {}/{} ({} reports)",
                    (i / BATCH_SIZE) + 1,
                    (validNumbers.size() + BATCH_SIZE - 1) / BATCH_SIZE,
                    reports.size());
        }

        log.info("Successfully created {} reports", validNumbers.size());
    }

    private void createChatContactsInBatches(Long userId, List<String> mobileNumbers, Long countryId) {
        log.info("Creating chat contacts for {} numbers in batches of {}", mobileNumbers.size(), BATCH_SIZE);

        for (int i = 0; i < mobileNumbers.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, mobileNumbers.size());
            List<String> batch = mobileNumbers.subList(i, end);

            chatContactService.ensureContactsExist(userId, batch, countryId);

            log.debug("Created contacts batch {}/{} ({} contacts)",
                    (i / BATCH_SIZE) + 1,
                    (mobileNumbers.size() + BATCH_SIZE - 1) / BATCH_SIZE,
                    batch.size());
        }

        log.info("Successfully ensured {} chat contacts exist", mobileNumbers.size());
    }

    private Report createReport(Long userId, Long broadcastId, String mobile) {
        return Report.builder()
                .userId(userId)
                .broadcastId(broadcastId)
                .mobile(mobile)
                .type("template")
                .status("PENDING")
                .messageStatus("QUEUED")
                .platform(Platform.web)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void dispatchMessages(
            Long userId,
            List<String> validNumbers,
            TemplateDto templateDto,
            SendTemplateRequestDto request,
            Long broadcastId,
            WhatsappAccount whatsappAccount) {

        log.info("Building sendable templates for {} numbers", validNumbers.size());

        WhatsappAccountInfoDto accountInfo = WhatsappAccountInfoDto.builder()
                .phoneNumberId(whatsappAccount.getWhatsappNoId())
                .accessToken(whatsappAccount.getParmenentToken())
                .build();

        List<MessageRequest> messageRequests = templateBuilderService.buildSendableTemplates(
                userId, validNumbers, templateDto, request);

        log.info("Building dispatch items for {} messages", messageRequests.size());

        List<BroadcastDispatchItemDto> dispatchItems = new ArrayList<>();

        for (MessageRequest messageRequest : messageRequests) {
            try {
                String payload = objectMapper
                        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                        .writeValueAsString(messageRequest);

                BroadcastDispatchItemDto item = BroadcastDispatchItemDto.builder()
                        .broadcastId(broadcastId)
                        .mobileNo(messageRequest.getTo())
                        .payload(payload)
                        .build();

                dispatchItems.add(item);

            } catch (Exception e) {
                log.error("Error serializing message for {}", messageRequest.getTo(), e);
            }
        }

        DispatchRequestDto dispatchRequest = DispatchRequestDto.builder()
                .items(dispatchItems)
                .accountInfo(accountInfo)
                .build();

        log.info("Dispatching {} items to messaging service", dispatchItems.size());

        FacebookApiResponse<BroadcastDispatchResponseDto> response = messagingClient.dispatchMessage(dispatchRequest);

        if (response.isSuccess()) {
            BroadcastDispatchResponseDto data = response.getData();
            log.info("Dispatch completed. Total: {}, Failed: {}, Message: {}",
                    data.getData().getTotalDispatched(),
                    data.getData().getFailedCount(),
                    data.getMessage());
        } else {
            log.error("Dispatch failed: {}", response.getErrorMessage());
            throw new RuntimeException("Failed to dispatch messages: " + response.getErrorMessage());
        }
    }
}