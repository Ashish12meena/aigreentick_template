package com.aigreentick.services.template.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.dto.response.PhoneBookResponseDto;
import com.aigreentick.services.template.repository.ChatContactsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatContactServiceImpl {
    private final ChatContactsRepository chatcontactRepo;

    public PhoneBookResponseDto getParamsForPhoneNumbers(List<String> filteredMobileNumbers, List<String> keys,
            Long userId, String defaultValue) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getParamsForPhoneNumbers'");
    }
    
}
