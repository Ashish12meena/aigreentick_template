package com.aigreentick.services.template.repository.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.account.WhatsappAccount;

@Repository
public interface WhatsappAccountRepository extends JpaRepository<WhatsappAccount, Long> {

    /**
     * Find WhatsApp account by user ID
     */
    List<WhatsappAccount> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * Find active WhatsApp account by user ID
     */
    @Query("SELECT w FROM WhatsappAccount w WHERE w.userId = :userId " +
           "AND w.status = '1' AND w.deletedAt IS NULL")
    Optional<WhatsappAccount> findActiveByUserId(@Param("userId") Long userId);

    /**
     * Find by WhatsApp Business ID (WABA ID)
     */
    Optional<WhatsappAccount> findByWhatsappBizIdAndDeletedAtIsNull(String whatsappBizId);

    /**
     * Find by WhatsApp number ID
     */
    Optional<WhatsappAccount> findByWhatsappNoIdAndDeletedAtIsNull(String whatsappNoId);

    /**
     * Find by WhatsApp number
     */
    Optional<WhatsappAccount> findByWhatsappNoAndDeletedAtIsNull(String whatsappNo);

    /**
     * Check if user has active WhatsApp account
     */
    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END " +
           "FROM WhatsappAccount w WHERE w.userId = :userId " +
           "AND w.status = '1' AND w.deletedAt IS NULL")
    boolean hasActiveAccount(@Param("userId") Long userId);

    /**
     * Find all active accounts
     */
    @Query("SELECT w FROM WhatsappAccount w WHERE w.status = '1' AND w.deletedAt IS NULL")
    List<WhatsappAccount> findAllActive();

    /**
     * Count active accounts by user
     */
    @Query("SELECT COUNT(w) FROM WhatsappAccount w WHERE w.userId = :userId " +
           "AND w.status = '1' AND w.deletedAt IS NULL")
    long countActiveByUserId(@Param("userId") Long userId);
}