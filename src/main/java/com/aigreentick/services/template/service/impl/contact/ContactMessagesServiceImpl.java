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

    @Value("${broadcast.batch-size:200}")
    private int batchSize;

    /**
     * Asynchronously creates ContactMessages using pre-collected ID maps.
     * No additional DB queries needed - IDs are passed directly.
     *
     * @param mobileToReportId  Map of mobile -> reportId (from saved reports)
     * @param mobileToContactId Map of mobile -> contactId (from saved contacts)
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
                log.debug("[ContactMessages] Skipping mobile: {} (reportId: {}, contactId: {})",
                        mobile, reportId, contactId);
            }
        }

        // Save remaining
        if (!allMessages.isEmpty()) {
            contactMessagesRepository.saveAll(allMessages);
            log.debug("[ContactMessages] Saved final batch of {} records", allMessages.size());
        }
    }

    // Other helper methods...

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