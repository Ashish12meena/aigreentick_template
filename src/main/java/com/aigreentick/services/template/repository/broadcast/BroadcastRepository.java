package com.aigreentick.services.template.repository.broadcast;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.broadcast.Broadcast;

@Repository
public interface BroadcastRepository extends JpaRepository<Broadcast, Long> {

    /**
     * Find broadcasts that are ready for execution.
     * 
     * Fetches broadcasts whose scheduleAt falls between:
     * - Last 10 minutes (catchup window for missed broadcasts)
     * - Next 2 minutes (look-ahead window)
     * 
     * Only returns broadcasts with status='1' (PENDING/ACTIVE)
     * Ordered by scheduleAt ascending (oldest first)
     * 
     * @param startWindow Start of time window (now - 10 minutes)
     * @param endWindow   End of time window (now + 2 minutes)
     * @param status      Status filter (should be '1' for PENDING)
     * @return List of broadcasts ready for execution
     */
    @Query("""
                SELECT b FROM Broadcast b
                WHERE b.scheduleAt BETWEEN :startWindow AND :endWindow
                AND b.status = :status
                AND b.deletedAt IS NULL
                ORDER BY b.scheduleAt ASC
            """)
    List<Broadcast> findPendingScheduledBroadcasts(
            @Param("startWindow") LocalDateTime startWindow,
            @Param("endWindow") LocalDateTime endWindow,
            @Param("status") String status);
}
