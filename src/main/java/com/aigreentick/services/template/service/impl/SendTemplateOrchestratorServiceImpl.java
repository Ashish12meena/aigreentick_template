package com.aigreentick.services.template.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.dto.build.TemplateDto;
import com.aigreentick.services.template.dto.request.SendTemplateRequestDto;
import com.aigreentick.services.template.dto.response.AccessTokenCredentials;
import com.aigreentick.services.template.dto.response.TemplateResponseDto;
import com.aigreentick.services.template.enums.MessageCategory;
import com.aigreentick.services.template.enums.TemplateCategory;
import com.aigreentick.services.template.exceptions.InsufficientBalanceException;
import com.aigreentick.services.template.mapper.TemplateMapper;
import com.aigreentick.services.template.model.Broadcast;
import com.aigreentick.services.template.model.Template;
import com.aigreentick.services.template.model.User;
import com.aigreentick.services.template.model.WhatsappAccount;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class SendTemplateOrchestratorServiceImpl {
    private final WhatsappAccountServiceImpl whatsappAccountServiceImpl;
    private final TemplateServiceImpl templateServiceImpl;
    private final TemplateMapper templateMapper;
    private final UserServiceImpl userServiceImpl;

    public TemplateResponseDto broadcastTemplate(SendTemplateRequestDto request, Long userId) {
        User user = userServiceImpl.getActiveUserById(userId);

        // // ========== STEP 1: Get WhatsApp Configuration ==========
        WhatsappAccount config = whatsappAccountServiceImpl.getActiveAccountByUserId(user.getId());


        // // ========== STEP 2: Get and map Template ==========
        Template template = templateServiceImpl.getTemplateById(request.getTemlateId());
        TemplateDto templateDto = templateMapper.toTemplateDto(template);

        // // ========== STEP 3: Calculate Pricing Based on Template Category ==========
        BigDecimal pricePerMessage = getPricePerMessage(config.getUserId(), template.getCategory(),user);


        // // ========== STEP 4: Filter Blacklisted Numbers ==========
        List<String> validNumbers = filterBlacklistedNumbers(
                config.getUserId(),
                request.getMobileNumbers());

        // // ========== STEP 6: Validate Wallet Balance ==========
        BigDecimal totalDeduction = pricePerMessage.multiply(new BigDecimal(validNumbers.size()));
        if (BigDecimal.valueOf(user.getBalance()).compareTo(totalDeduction) < 0) {
            throw new InsufficientBalanceException("Insufficient wallet balance.", 402);
        }

        // // ========== STEP 7: Create Broadcast Record ==========
        Broadcast broadcast = createBroadcastRecord(request, user, config, validNumbers);

        // // ========== STEP 8: Create Broadcast Media (if applicable) ==========
        if (Boolean.TRUE.equals(request.getIsMedia())) {
            createBroadcastMedia(broadcast.getId(), request);
        }

        // // ========== STEP 9: Deduct Wallet Balance ==========
        deductWalletBalance(user, totalDeduction, broadcast.getId());

        // // ========== STEP 10 & 11: Create Reports and Contacts in Batches ==========
        log.info("=== Broadcast before report insert: {} ===", LocalDateTime.now());
        createReportsAndContactsInBatches(user.getId(), broadcast.getId(), validNumbers);

        // // ========== STEP 13: build template and diespatch dispatch with payload broadcastid and mobilenumber and payload that is in string with snake case   ==========
        

        return null;

    }

    private void createReportsAndContactsInBatches(Long id, Long id2, List<String> validNumbers) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createReportsAndContactsInBatches'");
    }

    private void deductWalletBalance(User user, BigDecimal totalDeduction, Long id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'deductWalletBalance'");
    }

    private void createBroadcastMedia(Long id, SendTemplateRequestDto request) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createBroadcastMedia'");
    }

    private Broadcast createBroadcastRecord(SendTemplateRequestDto request, User user, WhatsappAccount config,
            List<String> validNumbers) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createBroadcastRecord'");
    }

    private List<String> filterBlacklistedNumbers(Long userId, List<String> mobileNumbers) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'filterBlacklistedNumbers'");
    }

    private BigDecimal getPricePerMessage(Long userId, String templateCategory, User user) {
             Double charge;

        switch (TemplateCategory.valueOf(templateCategory)) {
            case AUTHENTICATION ->
                charge = user.getAuthMsgCharge();

            case UTILITY ->
                charge = user.getUtiltyMsgCharge();

            case MARKETING ->
                charge = user.getMarketMsgCharge();

            default -> throw new IllegalArgumentException(
                    "Unsupported message category: " + templateCategory);
        }

        if (charge <=0 || charge == null ) {
            throw new IllegalStateException(
                    "Charge not configured for userId=" + userId + ", category=" + templateCategory);
        }

        return BigDecimal.valueOf(charge);
    }
}
