package com.aigreentick.services.template.repository.template;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.template.MediaResumable;


@Repository
public interface ResumableMediaRepository extends JpaRepository<MediaResumable,Long> {
    
}
