package com.aigreentick.services.template.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aigreentick.services.template.model.Report;
import com.aigreentick.services.template.repository.ReportRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl {
    private final ReportRepository reportRepository;

    /**
     * Save a report
     */
    @Transactional
    public Report save(Report report) {
        log.debug("Saving report for mobile: {}", report.getMobile());
        return reportRepository.save(report);
    }

    /**
     * Save reports in bulk
     */
    @Transactional
    public List<Report> saveAll(List<Report> reports) {
        log.info("Saving {} reports in bulk", reports.size());
        return reportRepository.saveAll(reports);
    }

    /**
     * Get reports by broadcast ID with pagination
     */
    public Page<Report> getReportsByBroadcastId(Long broadcastId, int page, int size) {
        log.debug("Fetching reports for broadcastId: {}", broadcastId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return reportRepository.findByBroadcastIdAndDeletedAtIsNull(broadcastId, pageable);
    }

    /**
     * Get reports by user ID with pagination
     */
    public Page<Report> getReportsByUserId(Long userId, int page, int size) {
        log.debug("Fetching reports for userId: {}", userId);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return reportRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
    }

    /**
     * Get report by message ID
     */
    public Report getReportByMessageId(String messageId) {
        log.debug("Fetching report by messageId: {}", messageId);
        return reportRepository.findByMessageIdAndDeletedAtIsNull(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found for messageId: " + messageId));
    }

    /**
     * Get delivery statistics for a broadcast
     */
    public Map<String, Long> getDeliveryStats(Long broadcastId) {
        log.debug("Fetching delivery stats for broadcastId: {}", broadcastId);
        
        List<Object[]> results = reportRepository.getDeliveryStatsByBroadcastId(broadcastId);
        
        return results.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],    // status
                        row -> (Long) row[1]        // count
                ));
    }

    /**
     * Count reports by broadcast
     */
    public long countByBroadcastId(Long broadcastId) {
        return reportRepository.countByBroadcastIdAndDeletedAtIsNull(broadcastId);
    }

    /**
     * Count reports by status
     */
    public long countByStatus(Long broadcastId, String status) {
        return reportRepository.countByBroadcastIdAndStatus(broadcastId, status);
    }

    /**
     * Get failed reports for retry
     */
    public List<Report> getFailedReports(Long broadcastId) {
        log.debug("Fetching failed reports for broadcastId: {}", broadcastId);
        return reportRepository.findFailedReportsByBroadcastId(broadcastId);
    }

    /**
     * Get reports by date range
     */
    public List<Report> getReportsByDateRange(Long userId, LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Fetching reports for userId: {} between {} and {}", userId, startDate, endDate);
        return reportRepository.findByUserIdAndDateRange(userId, startDate, endDate);
    }

    /**
     * Get reports by mobile number
     */
    public List<Report> getReportsByMobile(String mobile, Long userId) {
        log.debug("Fetching reports for mobile: {} and userId: {}", mobile, userId);
        return reportRepository.findByMobileAndUserIdAndDeletedAtIsNull(mobile, userId);
    }

    /**
     * Update report status
     */
    @Transactional
    public Report updateStatus(String messageId, String newStatus) {
        log.info("Updating report status for messageId: {} to {}", messageId, newStatus);
        
        Report report = getReportByMessageId(messageId);
        report.setStatus(newStatus);
        report.setMessageStatus(newStatus);
        report.setUpdatedAt(LocalDateTime.now());
        
        return reportRepository.save(report);
    }
}