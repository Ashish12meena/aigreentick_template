package com.aigreentick.services.template.model.contact;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "contacts_messages")
@Data
public class ContactMessages {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

