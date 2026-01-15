package com.aigreentick.services.template.repository.template;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.template.model.template.MediaResumable;

public interface ResumableMediaRepository extends JpaRepository<MediaResumable,Long> {
    
}
