package com.aigreentick.services.template.repository.broadcast;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.broadcast.BroadcastMedia;


@Repository
public interface BroadcastMediaRepository extends JpaRepository<BroadcastMedia,Long> {
    
}
