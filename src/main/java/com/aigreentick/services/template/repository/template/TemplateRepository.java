package com.aigreentick.services.template.repository.template;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.template.Template;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    boolean existsByNameAndUserIdAndDeletedAtIsNull(String name, Long userId);

    /**
     * Find all waId (Facebook template IDs) for a given user where template is not
     * deleted
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

    // ============== NEW METHODS FOR SYNC OPTIMIZATION ==============

    /**
     * Find templates by userId and status (for finding new_created templates)
     */
    List<Template> findByUserIdAndStatusAndDeletedAtIsNull(Long userId, String status);

    /**
     * Find templates by userId, status, and names (for matching with Facebook approved templates)
     */
    @Query("SELECT t FROM Template t WHERE t.userId = :userId AND t.status = :status " +
            "AND t.name IN :names AND t.deletedAt IS NULL")
    List<Template> findByUserIdAndStatusAndNameIn(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("names") Set<String> names);

    /**
     * Find template by userId, name, and status
     */
    Optional<Template> findByUserIdAndNameAndStatusAndDeletedAtIsNull(
            Long userId, String name, String status);

    /**
     * Find all template names for a user where status is new_created
     */
    @Query("SELECT t.name FROM Template t WHERE t.userId = :userId AND t.status = :status AND t.deletedAt IS NULL")
    Set<String> findNamesByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    // ============== PAGINATED QUERY METHODS ==============

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

    @Modifying
    @Query("DELETE FROM Template t WHERE t.userId = :userId")
    void deleteAllByUserId(Long userId);
}