package com.aigreentick.services.template.service.impl.broadcast;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.model.broadcast.Broadcast;
import com.aigreentick.services.template.repository.broadcast.BroadcastRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastServiceImpl {

    private static final int DEFAULT_LOOKBACK = 10;
    private static final int DEFAULT_LOOKAHEAD = 2;

    private static final int MAX_LOOKBACK_MINUTES = 60;
     private static final int MAX_LOOKHEAD_MINUTES = 10;

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

    /**
     * Get broadcasts ready for execution with custom time window.
     * 
     * @param lookbackMinutes  Minutes to look back from now
     * @param lookaheadMinutes Minutes to look ahead from now
     * @return List of broadcasts ready for execution
     */
    @Transactional(readOnly = true)
    public List<Broadcast> getPendingScheduledBroadcasts(Integer lookbackMinutes, Integer lookaheadMinutes) {

        int safeLookback = (lookbackMinutes == null || lookbackMinutes <= 0)
                ? DEFAULT_LOOKBACK
                : Math.min(lookbackMinutes, MAX_LOOKBACK_MINUTES);

        int safeLookahead = (lookaheadMinutes == null || lookaheadMinutes <= 0)
                ? DEFAULT_LOOKAHEAD
                : Math.min(lookaheadMinutes, MAX_LOOKHEAD_MINUTES);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startWindow = now.minusMinutes(safeLookback);
        LocalDateTime endWindow = now.plusMinutes(safeLookahead);

        log.info("Fetching scheduled broadcasts between {} and {} with status='1'",
                startWindow, endWindow);

        List<Broadcast> broadcasts = broadcastRepository.findPendingScheduledBroadcasts(
                startWindow, endWindow, "1");

        log.info("Found {} pending scheduled broadcasts", broadcasts.size());

        return broadcasts;
    }

}