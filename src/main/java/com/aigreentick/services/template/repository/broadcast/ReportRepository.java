package com.aigreentick.services.template.repository.broadcast;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.broadcast.Report;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * Find reports by broadcast ID
     */
    List<Report> findByBroadcastIdAndDeletedAtIsNull(Long broadcastId);

    /**
     * Find reports by broadcast ID with pagination
     */
    Page<Report> findByBroadcastIdAndDeletedAtIsNull(Long broadcastId, Pageable pageable);

    /**
     * Find reports by user ID
     */
    Page<Report> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    /**
     * Find report by message ID
     */
    Optional<Report> findByMessageIdAndDeletedAtIsNull(String messageId);

    /**
     * Find reports by mobile number
     */
    List<Report> findByMobileAndUserIdAndDeletedAtIsNull(String mobile, Long userId);

    /**
     * Count reports by broadcast ID
     */
    long countByBroadcastIdAndDeletedAtIsNull(Long broadcastId);

    /**
     * Count reports by status for a broadcast
     */
    @Query("SELECT COUNT(r) FROM Report r WHERE r.broadcastId = :broadcastId " +
           "AND r.status = :status AND r.deletedAt IS NULL")
    long countByBroadcastIdAndStatus(
            @Param("broadcastId") Long broadcastId,
            @Param("status") String status);

    /**
     * Find reports by date range
     */
    @Query("SELECT r FROM Report r WHERE r.userId = :userId " +
           "AND r.createdAt BETWEEN :startDate AND :endDate " +
           "AND r.deletedAt IS NULL")
    List<Report> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Get delivery statistics for a broadcast
     */
    @Query("SELECT r.status, COUNT(r) FROM Report r " +
           "WHERE r.broadcastId = :broadcastId AND r.deletedAt IS NULL " +
           "GROUP BY r.status")
    List<Object[]> getDeliveryStatsByBroadcastId(@Param("broadcastId") Long broadcastId);

    /**
     * Find failed reports for retry
     */
    @Query("SELECT r FROM Report r WHERE r.broadcastId = :broadcastId " +
           "AND r.status IN ('failed', 'error') AND r.deletedAt IS NULL")
    List<Report> findFailedReportsByBroadcastId(@Param("broadcastId") Long broadcastId);
}