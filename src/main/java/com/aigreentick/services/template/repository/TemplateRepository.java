package com.aigreentick.services.template.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.Template;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    boolean existsByNameAndUserIdAndDeletedAtIsNull(String name, Long userId);

    /**
     * Find all waId (Facebook template IDs) for a given user where template is not deleted
     */
    @Query("SELECT t.waId FROM Template t WHERE t.userId = :userId AND t.deletedAt IS NULL AND t.waId IS NOT NULL")
    Set<String> findWaIdsByUserId(@Param("userId") Long userId);

    /**
     * Soft delete templates by waId and userId
     * Returns the count of updated rows
     */
    @Modifying
    @Query("UPDATE Template t SET t.deletedAt = :deletedAt, t.updatedAt = :updatedAt " +
           "WHERE t.waId IN :waIds AND t.userId = :userId AND t.deletedAt IS NULL")
    int softDeleteByWaIdInAndUserId(
            @Param("waIds") Set<String> waIds,
            @Param("userId") Long userId,
            @Param("deletedAt") LocalDateTime deletedAt,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * Hard delete templates by waId and userId (if needed)
     */
    @Modifying
    @Query("DELETE FROM Template t WHERE t.waId IN :waIds AND t.userId = :userId")
    int deleteByWaIdInAndUserId(@Param("waIds") Set<String> waIds, @Param("userId") Long userId);

    /**
     * Find templates by userId that are not deleted
     */
    List<Template> findByUserIdAndDeletedAtIsNull(Long userId);

    /**
     * Check if template exists by waId and userId
     */
    boolean existsByWaIdAndUserIdAndDeletedAtIsNull(String waId, Long userId);

     // ============== NEW PAGINATED QUERY METHODS ==============

    /**
     * Find templates by userId with pagination (no filters)
     */
    Page<Template> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    /**
     * Find templates by userId and status with pagination
     */
    Page<Template> findByUserIdAndStatusAndDeletedAtIsNull(Long userId, String status, Pageable pageable);

    /**
     * Find templates by userId with name search (case-insensitive)
     */
    @Query("SELECT t FROM Template t WHERE t.userId = :userId AND t.deletedAt IS NULL " +
           "AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Template> findByUserIdAndNameContaining(
            @Param("userId") Long userId,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Find templates by userId, status, and name search
     */
    @Query("SELECT t FROM Template t WHERE t.userId = :userId AND t.deletedAt IS NULL " +
           "AND t.status = :status AND LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Template> findByUserIdAndStatusAndNameContaining(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable);
}