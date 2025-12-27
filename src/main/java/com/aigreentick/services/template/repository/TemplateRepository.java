package com.aigreentick.services.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.Template;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {
    
    boolean existsByNameAndUserIdAndDeletedAtIsNull(String name, Long userId);
}