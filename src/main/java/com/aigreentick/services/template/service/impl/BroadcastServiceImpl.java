package com.aigreentick.services.template.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.model.Broadcast;
import com.aigreentick.services.template.repository.BroadcastRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastServiceImpl {
    private final BroadcastRepository broadcastRepository;

    /**
     * Save a broadcast record
     */
    @Transactional
    public Broadcast save(Broadcast broadcast) {
        log.info("Saving broadcast for userId: {}, templateId: {}", 
                broadcast.getUserId(), broadcast.getTemplateId());
        return broadcastRepository.save(broadcast);
    }

    /**
     * Get broadcast by ID
     */
    public Broadcast getBroadcastById(Long id) {
        log.debug("Fetching broadcast by ID: {}", id);
        return broadcastRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Broadcast not found with ID: " + id));
    }

    /**
     * Get all broadcasts with pagination
     */
    public Page<Broadcast> getAllBroadcasts(int page, int size) {
        log.debug("Fetching all broadcasts - page: {}, size: {}", page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return broadcastRepository.findAll(pageable);
    }

    /**
     * Update broadcast status
     */
    // @Transactional
    // public Broadcast updateStatus(Long broadcastId, Broadcast.Status newStatus) {
    //     log.info("Updating broadcast {} status to {}", broadcastId, newStatus);

        
    //     Broadcast broadcast = getBroadcastById(broadcastId);
    //     if (newStatus==Broadcast.Status.) {
            
    //     }
    //     broadcast.setStatus();
    //     broadcast.setUpdatedAt(LocalDateTime.now());
        
    //     return broadcastRepository.save(broadcast);
    // }

    /**
     * Save multiple broadcasts
     */
    @Transactional
    public List<Broadcast> saveAll(List<Broadcast> broadcasts) {
        log.info("Saving {} broadcasts in bulk", broadcasts.size());
        return broadcastRepository.saveAll(broadcasts);
    }

    /**
     * Soft delete broadcast
     */
    @Transactional
    public void deleteBroadcast(Long broadcastId) {
        log.info("Soft deleting broadcast: {}", broadcastId);
        
        Broadcast broadcast = getBroadcastById(broadcastId);
        broadcast.setDeletedAt(LocalDateTime.now());
        broadcast.setUpdatedAt(LocalDateTime.now());
        
        broadcastRepository.save(broadcast);
    }
}