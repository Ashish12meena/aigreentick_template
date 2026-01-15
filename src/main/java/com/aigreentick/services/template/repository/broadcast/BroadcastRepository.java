package com.aigreentick.services.template.repository.broadcast;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.template.model.broadcast.Broadcast;

public interface BroadcastRepository extends JpaRepository<Broadcast,Long>{
    
}
