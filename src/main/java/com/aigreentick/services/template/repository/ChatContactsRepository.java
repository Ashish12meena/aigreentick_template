package com.aigreentick.services.template.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.aigreentick.services.template.model.ChatContacts;

@Repository
public interface ChatContactsRepository extends JpaRepository<ChatContacts,Long>{
    
}
