package com.aigreentick.services.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.template.model.MediaResumable;

public interface ResumableMediaRepository extends JpaRepository<MediaResumable,Long> {
    
}
