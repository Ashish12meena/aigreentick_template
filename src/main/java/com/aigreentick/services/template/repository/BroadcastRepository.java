package com.aigreentick.services.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aigreentick.services.template.model.Broadcast;

public interface BroadcastRepository extends JpaRepository<Broadcast,Long>{
    
}
