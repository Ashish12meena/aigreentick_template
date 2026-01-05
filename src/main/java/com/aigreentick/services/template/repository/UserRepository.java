package com.aigreentick.services.template.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email
     */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /**
     * Find user by mobile
     */
    Optional<User> findByMobileAndDeletedAtIsNull(String mobile);

    /**
     * Find user by API token
     */
    Optional<User> findByApiTokenAndDeletedAtIsNull(String apiToken);

    /**
     * Check if email exists
     */
    boolean existsByEmailAndDeletedAtIsNull(String email);

    /**
     * Check if mobile exists
     */
    boolean existsByMobileAndDeletedAtIsNull(String mobile);

    /**
     * Find active user by ID (status = '1' means active)
     */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL AND u.status = '1'")
    Optional<User> findActiveById(@Param("id") Long id);

    /**
     * Check if user has sufficient balance
     */
    @Query("SELECT CASE WHEN u.balance >= :amount THEN true ELSE false END " +
           "FROM User u WHERE u.id = :userId AND u.deletedAt IS NULL")
    boolean hasSufficientBalance(@Param("userId") Long userId, @Param("amount") Double amount);

    /**
     * Find user with account admin
     */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findByIdWithAccountAdmin(@Param("id") Long id);

    /**
     * Get authentication message charge for user
     */
    @Query("SELECT u.authMsgCharge FROM User u WHERE u.id = :userId")
    Double findAuthChargeByUserId(@Param("userId") Long userId);

    /**
     * Get utility message charge for user
     */
    @Query("SELECT u.utiltyMsgCharge FROM User u WHERE u.id = :userId")
    Double findUtilityChargeByUserId(@Param("userId") Long userId);

    /**
     * Get marketing message charge for user
     */
    @Query("SELECT u.marketMsgCharge FROM User u WHERE u.id = :userId")
    Double findMarketingChargeByUserId(@Param("userId") Long userId);
}