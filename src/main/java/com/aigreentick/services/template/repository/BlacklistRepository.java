package com.aigreentick.services.template.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.Blacklist;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, Long> {

    /**
     * Find blacklist entry by userId and mobile
     */
    Optional<Blacklist> findByUserIdAndMobileAndDeletedAtIsNull(Long userId, String mobile);

    /**
     * Check if mobile is blacklisted for user
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END " +
           "FROM Blacklist b WHERE b.userId = :userId AND b.mobile = :mobile " +
           "AND b.isBlocked = '1' AND b.deletedAt IS NULL")
    boolean isMobileBlocked(@Param("userId") Long userId, @Param("mobile") String mobile);

    /**
     * Find all blocked numbers for a user
     */
    @Query("SELECT b FROM Blacklist b WHERE b.userId = :userId " +
           "AND b.isBlocked = '1' AND b.deletedAt IS NULL")
    List<Blacklist> findBlockedNumbersByUserId(@Param("userId") Long userId);

    /**
     * Find all blacklist entries for user (including non-blocked)
     */
    List<Blacklist> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * Bulk check if mobiles are blocked
     */
    @Query("SELECT b.mobile FROM Blacklist b WHERE b.userId = :userId " +
           "AND b.mobile IN :mobiles AND b.isBlocked = '1' AND b.deletedAt IS NULL")
    List<String> findBlockedMobilesInList(
            @Param("userId") Long userId,
            @Param("mobiles") List<String> mobiles);

    /**
     * Count blocked numbers for user
     */
    @Query("SELECT COUNT(b) FROM Blacklist b WHERE b.userId = :userId " +
           "AND b.isBlocked = '1' AND b.deletedAt IS NULL")
    long countBlockedByUserId(@Param("userId") Long userId);
}