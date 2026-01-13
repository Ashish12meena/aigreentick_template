package com.aigreentick.services.template.service.impl.other;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.model.Blacklist;
import com.aigreentick.services.template.repository.BlacklistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlacklistServiceImpl {
    private final BlacklistRepository blacklistRepository;

    /**
     * Check if a mobile number is blacklisted for a user
     */
    public boolean isMobileBlocked(Long userId, String mobile) {
        return blacklistRepository.isMobileBlocked(userId, mobile);
    }

    /**
     * Filter out blocked numbers from a list
     */
    public List<String> filterBlockedNumbers(Long userId, List<String> mobiles) {
        log.debug("Filtering blocked numbers for userId: {}", userId);
        
        List<String> blockedNumbers = blacklistRepository.findBlockedMobilesInList(userId, mobiles);
        
        if (!blockedNumbers.isEmpty()) {
            log.info("Found {} blocked numbers out of {} total", blockedNumbers.size(), mobiles.size());
        }
        
        return mobiles.stream()
                .filter(mobile -> !blockedNumbers.contains(mobile))
                .toList();
    }

    /**
     * Add a number to blacklist
     */
    @Transactional
    public Blacklist blockNumber(Long userId, String mobile, Long countryId) {
        log.info("Blocking number {} for userId: {}", mobile, userId);
        
        Blacklist blacklist = blacklistRepository
                .findByUserIdAndMobileAndDeletedAtIsNull(userId, mobile)
                .orElse(new Blacklist());
        
        blacklist.setUserId(userId);
        blacklist.setMobile(mobile);
        blacklist.setCountryId(countryId);
        blacklist.setIsBlocked("1");
        blacklist.setUpdatedAt(LocalDateTime.now());
        
        if (blacklist.getId() == null) {
            blacklist.setCreatedAt(LocalDateTime.now());
        }
        
        return blacklistRepository.save(blacklist);
    }

    /**
     * Unblock a number
     */
    @Transactional
    public Blacklist unblockNumber(Long userId, String mobile) {
        log.info("Unblocking number {} for userId: {}", mobile, userId);
        
        Blacklist blacklist = blacklistRepository
                .findByUserIdAndMobileAndDeletedAtIsNull(userId, mobile)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Blacklist entry not found for mobile: " + mobile));
        
        blacklist.setIsBlocked("0");
        blacklist.setUpdatedAt(LocalDateTime.now());
        
        return blacklistRepository.save(blacklist);
    }

    /**
     * Get all blocked numbers for a user
     */
    public List<Blacklist> getBlockedNumbers(Long userId) {
        log.debug("Fetching blocked numbers for userId: {}", userId);
        return blacklistRepository.findBlockedNumbersByUserId(userId);
    }

    /**
     * Get count of blocked numbers
     */
    public long getBlockedCount(Long userId) {
        return blacklistRepository.countBlockedByUserId(userId);
    }

    /**
     * Remove from blacklist (soft delete)
     */
    @Transactional
    public void removeFromBlacklist(Long userId, String mobile) {
        log.info("Removing {} from blacklist for userId: {}", mobile, userId);
        
        Blacklist blacklist = blacklistRepository
                .findByUserIdAndMobileAndDeletedAtIsNull(userId, mobile)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Blacklist entry not found for mobile: " + mobile));
        
        blacklist.setDeletedAt(LocalDateTime.now());
        blacklist.setUpdatedAt(LocalDateTime.now());
        
        blacklistRepository.save(blacklist);
    }
}