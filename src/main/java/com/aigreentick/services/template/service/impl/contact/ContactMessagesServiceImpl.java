package com.aigreentick.services.template.service.impl.contact;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.model.contact.ContactMessages;
import com.aigreentick.services.template.repository.contact.ContactMessagesRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactMessagesServiceImpl {

    private final ContactMessagesRepository contactMessagesRepository;
    private final ChatContactServiceImpl chatContactService;

    @Value("${broadcast.batch-size:200}")
    private int batchSize;

    /**
     * NEW: Chained async method that handles both:
     * 1. Creating/fetching ChatContacts and getting their IDs
     * 2. Creating ContactMessages linking reports to contacts
     * 
     * This is fire-and-forget - orchestrator doesn't wait for completion.
     *
     * @param mobileToReportId Map of mobile -> reportId (from saved reports)
     * @param userId           User ID for contact ownership
     * @param countryId        Country ID for new contacts
     */
    @Async("messageDispatchExecutor")
    public void createContactsAndLinkMessagesAsync(
            Map<String, Long> mobileToReportId,
            Long userId,
            Long countryId) {

        log.info("[ContactsAndMessages] Starting chained async for {} mobiles, userId={}",
                mobileToReportId.size(), userId);

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Create/fetch contacts and get their IDs
            List<String> mobileNumbers = new ArrayList<>(mobileToReportId.keySet());
            
            log.info("[ContactsAndMessages] Step 1: Ensuring contacts exist for {} numbers", mobileNumbers.size());
            Map<String, Long> mobileToContactId = chatContactService.ensureContactsExistAndGetIds(
                    userId, mobileNumbers, countryId);
            
            log.info("[ContactsAndMessages] Step 1 complete: {} contacts ready", mobileToContactId.size());

            // Step 2: Create ContactMessages linking reports to contacts
            log.info("[ContactsAndMessages] Step 2: Creating contact-message links");
            createContactMessagesInBatches(mobileToReportId, mobileToContactId, userId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[ContactsAndMessages] Completed in {}ms - {} contacts, {} links created",
                    duration, mobileToContactId.size(), mobileToReportId.size());

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[ContactsAndMessages] Failed after {}ms for userId={}: {}",
                    duration, userId, e.getMessage(), e);
            // Don't rethrow - this is fire-and-forget
        }
    }

    /**
     * DEPRECATED: Use createContactsAndLinkMessagesAsync instead.
     * Kept for backward compatibility if needed.
     * 
     * Asynchronously creates ContactMessages using pre-collected ID maps.
     */
    @Async("messageDispatchExecutor")
    public void createContactMessagesAsync(
            Map<String, Long> mobileToReportId,
            Map<String, Long> mobileToContactId,
            Long userId) {

        log.info("[ContactMessages] Starting async creation for {} mobiles", mobileToReportId.size());

        long startTime = System.currentTimeMillis();

        try {
            createContactMessagesInBatches(mobileToReportId, mobileToContactId, userId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[ContactMessages] Completed in {}ms, created {} records",
                    duration, mobileToReportId.size());

        } catch (Exception e) {
            log.error("[ContactMessages] Failed to create contact messages", e);
            // Don't rethrow - fire-and-forget
        }
    }

    /**
     * Creates ContactMessages in batches using pre-collected ID maps.
     */
    @Transactional
    public void createContactMessagesInBatches(
            Map<String, Long> mobileToReportId,
            Map<String, Long> mobileToContactId,
            Long userId) {

        List<String> mobiles = new ArrayList<>(mobileToReportId.keySet());
        log.info("[ContactMessages] Processing {} mobiles in batches of {}", mobiles.size(), batchSize);

        List<ContactMessages> allMessages = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int skippedCount = 0;

        for (String mobile : mobiles) {
            Long reportId = mobileToReportId.get(mobile);
            Long contactId = mobileToContactId.get(mobile);

            if (reportId != null && contactId != null) {
                ContactMessages cm = ContactMessages.builder()
                        .contactId(contactId)
                        .reportId(reportId)
                        .userId(userId)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

                allMessages.add(cm);

                // Save in batches
                if (allMessages.size() >= batchSize) {
                    contactMessagesRepository.saveAll(allMessages);
                    log.debug("[ContactMessages] Saved batch of {} records", allMessages.size());
                    allMessages.clear();
                }
            } else {
                skippedCount++;
                log.debug("[ContactMessages] Skipping mobile: {} (reportId: {}, contactId: {})",
                        mobile, reportId, contactId);
            }
        }

        // Save remaining
        if (!allMessages.isEmpty()) {
            contactMessagesRepository.saveAll(allMessages);
            log.debug("[ContactMessages] Saved final batch of {} records", allMessages.size());
        }

        if (skippedCount > 0) {
            log.warn("[ContactMessages] Skipped {} mobiles due to missing IDs", skippedCount);
        }
    }

    // ==================== EXISTING HELPER METHODS ====================

    @Transactional
    public ContactMessages save(ContactMessages contactMessages) {
        return contactMessagesRepository.save(contactMessages);
    }

    @Transactional
    public List<ContactMessages> saveAll(List<ContactMessages> list) {
        return contactMessagesRepository.saveAll(list);
    }

    public List<ContactMessages> getByContactId(Long contactId) {
        return contactMessagesRepository.findByContactId(contactId);
    }

    public List<ContactMessages> getByReportId(Long reportId) {
        return contactMessagesRepository.findByReportId(reportId);
    }

    public long countByBroadcastId(Long broadcastId) {
        return contactMessagesRepository.countByBroadcastId(broadcastId);
    }
}