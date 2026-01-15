package com.aigreentick.services.template.repository.contact;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.contact.ChatContacts;

@Repository
public interface ChatContactsRepository extends JpaRepository<ChatContacts, Integer> {
    
    /**
     * Find all contacts for a user by mobile numbers (excluding deleted).
     * Uses JOIN FETCH to eagerly load attributes to avoid N+1 queries.
     */
    @Query("SELECT DISTINCT c FROM ChatContacts c " +
           "LEFT JOIN FETCH c.attributes " +
           "WHERE c.userId = :userId " +
           "AND c.mobile IN :mobiles " +
           "AND c.deletedAt IS NULL")
    List<ChatContacts> findByUserIdAndMobileInAndDeletedAtIsNull(
            @Param("userId") Long userId,
            @Param("mobiles") List<String> mobiles);

    /**
     * Find a single contact by userId and mobile number.
     */
    @Query("SELECT c FROM ChatContacts c " +
           "LEFT JOIN FETCH c.attributes " +
           "WHERE c.userId = :userId " +
           "AND c.mobile = :mobile " +
           "AND c.deletedAt IS NULL")
    Optional<ChatContacts> findByUserIdAndMobileAndDeletedAtIsNull(
            @Param("userId") Long userId,
            @Param("mobile") String mobile);

    /**
     * Check if a contact exists for the given user and mobile.
     */
    boolean existsByUserIdAndMobileAndDeletedAtIsNull(Long userId, String mobile);

    /**
     * Find all contacts for a user (excluding deleted).
     */
    List<ChatContacts> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * Find contacts with broadcast allowed.
     */
    @Query("SELECT c FROM ChatContacts c " +
           "WHERE c.userId = :userId " +
           "AND c.allowedBroadcast = true " +
           "AND c.deletedAt IS NULL")
    List<ChatContacts> findBroadcastEnabledContacts(@Param("userId") Long userId);

    /**
     * Count total contacts for a user.
     */
    long countByUserIdAndDeletedAtIsNull(Long userId);
}