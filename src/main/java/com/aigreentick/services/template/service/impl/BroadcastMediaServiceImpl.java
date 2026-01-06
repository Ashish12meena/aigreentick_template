package com.aigreentick.services.template.service.impl;

import org.springframework.stereotype.Service;

import com.aigreentick.services.template.model.BroadcastMedia;
import com.aigreentick.services.template.repository.BroadcastMediaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastMediaServiceImpl {
    private final BroadcastMediaRepository broadcastMediaRepository;

    public BroadcastMedia save(BroadcastMedia broadcastMedia){
        return broadcastMediaRepository.save(broadcastMedia);
    }
}
