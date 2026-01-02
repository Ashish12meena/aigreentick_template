package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Data;
import java.sql.Timestamp;

@Data
@Entity
@Table(
    name = "chat_contacts",
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_user_mobile", columnNames = {"user_id", "mobile"})
    }
)
public class ChatContacts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "mobile", nullable = false, length = 200)
    private String mobile;

    @Column(name = "country_id", nullable = false, length = 10)
    private String countryId = "91";

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "status", nullable = false)
    private boolean status = true;

    @Column(name = "time")
    private Integer time;

    @Column(name = "allowed_sms")
    private boolean allowedSms = false;

    @Column(name = "allowed_broadcast", nullable = false)
    private boolean allowedBroadcast = true;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false, insertable = false)
    private Timestamp updatedAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;
}
