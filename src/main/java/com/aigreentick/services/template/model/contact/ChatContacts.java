package com.aigreentick.services.template.model.contact;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(
    name = "chat_contacts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "unique_user_mobile",
            columnNames = { "user_id", "mobile" }
        )
    }
)
public class ChatContacts {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

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
    private Byte status = 1;

    @Column(name = "time")
    private Integer time;

    @Column(name = "allowed_sms")
    private boolean allowedSms = false;

    @Column(name = "allowed_broadcast", nullable = false)
    private boolean allowedBroadcast = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "contact", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ContactAttributes> attributes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(IST);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now(IST);
    }

    @PreRemove
    protected void onDelete() {
        this.deletedAt = LocalDateTime.now(IST);
    }
}
