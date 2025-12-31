package com.aigreentick.services.template.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.dto.response.MetaTemplateIdOnly;
import com.aigreentick.services.template.model.Template;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {
    
    boolean existsByNameAndUserIdAndDeletedAtIsNull(String name, Long userId);

    List<MetaTemplateIdOnly> findMetaTemplateIdsByUserId(Long userId);
}