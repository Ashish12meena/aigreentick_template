package com.aigreentick.services.template.repository.broadcast;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.template.model.broadcast.BroadcastMedia;

public interface BroadcastMediaRepository extends JpaRepository<BroadcastMedia,Long> {
    
}
