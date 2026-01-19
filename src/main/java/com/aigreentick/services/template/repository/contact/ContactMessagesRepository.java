package com.aigreentick.services.template.repository.contact;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.contact.ContactMessages;

@Repository
public interface ContactMessagesRepository extends JpaRepository<ContactMessages, Long> {

    /**
     * Find contact messages by contact ID
     */
    List<ContactMessages> findByContactId(Long contactId);

    /**
     * Find contact messages by report ID
     */
    List<ContactMessages> findByReportId(Long reportId);

    /**
     * Check if a contact message link already exists
     */
    boolean existsByContactIdAndReportId(Long contactId, Long reportId);

    /**
     * Find contact messages by broadcast ID through reports
     */
    @Query("SELECT cm FROM ContactMessages cm WHERE cm.reportId IN " +
           "(SELECT r.id FROM Report r WHERE r.broadcastId = :broadcastId)")
    List<ContactMessages> findByBroadcastId(@Param("broadcastId") Long broadcastId);

    /**
     * Count contact messages for a broadcast
     */
    @Query("SELECT COUNT(cm) FROM ContactMessages cm WHERE cm.reportId IN " +
           "(SELECT r.id FROM Report r WHERE r.broadcastId = :broadcastId)")
    long countByBroadcastId(@Param("broadcastId") Long broadcastId);
}