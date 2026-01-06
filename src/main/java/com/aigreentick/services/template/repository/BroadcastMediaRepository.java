package com.aigreentick.services.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.template.model.BroadcastMedia;

public interface BroadcastMediaRepository extends JpaRepository<BroadcastMedia,Long> {
    
}
