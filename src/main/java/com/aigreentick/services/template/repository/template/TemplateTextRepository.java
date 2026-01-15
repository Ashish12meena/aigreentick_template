package com.aigreentick.services.template.repository.template;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.template.TemplateText;

@Repository
public interface TemplateTextRepository extends JpaRepository<TemplateText, Long> {

    /**
     * Find all variables for a template
     */
    List<TemplateText> findByTemplateIdAndDeletedAtIsNull(Long templateId);

    /**
     * Find variables by template and type
     */
    List<TemplateText> findByTemplateIdAndTypeAndDeletedAtIsNull(Long templateId, String type);

    /**
     * Find a specific variable by template, type, and index
     */
    @Query("SELECT t FROM TemplateText t WHERE t.template.id = :templateId " +
           "AND t.type = :type AND t.textIndex = :textIndex AND t.deletedAt IS NULL")
    TemplateText findByTemplateIdAndTypeAndTextIndex(
            @Param("templateId") Long templateId,
            @Param("type") String type,
            @Param("textIndex") Integer textIndex);

    /**
     * Find carousel variable by template, type, index, and card index
     */
    @Query("SELECT t FROM TemplateText t WHERE t.template.id = :templateId " +
           "AND t.type = :type AND t.textIndex = :textIndex " +
           "AND t.cardIndex = :cardIndex AND t.isCarousel = true AND t.deletedAt IS NULL")
    TemplateText findCarouselVariable(
            @Param("templateId") Long templateId,
            @Param("type") String type,
            @Param("textIndex") Integer textIndex,
            @Param("cardIndex") Integer cardIndex);

    /**
     * Update default value for a specific variable
     */
    @Modifying
    @Query("UPDATE TemplateText t SET t.defaultValue = :defaultValue, t.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE t.id = :id")
    int updateDefaultValue(@Param("id") Long id, @Param("defaultValue") String defaultValue);

    /**
     * Clear all default values for a template
     */
    @Modifying
    @Query("UPDATE TemplateText t SET t.defaultValue = NULL, t.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE t.template.id = :templateId")
    int clearAllDefaultValues(@Param("templateId") Long templateId);

    /**
     * Count variables for a template
     */
    long countByTemplateIdAndDeletedAtIsNull(Long templateId);

    /**
     * Check if a variable exists
     */
    boolean existsByIdAndTemplateId(Long id, Long templateId);
}