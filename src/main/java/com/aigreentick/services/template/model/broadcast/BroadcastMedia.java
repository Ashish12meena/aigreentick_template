package com.aigreentick.services.template.model.broadcast;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "broadcast_media", indexes = {
        @Index(name = "broadcast_medias_broadcast_id_foreign", columnList = "broadcast_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BroadcastMedia {
    
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "broadcast_id", nullable = false)
    private Long broadcastId;

    @Column(name = "type", nullable = false, length = 255)
    private String type;

    @Column(name = "url", columnDefinition = "text")
    private String url;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
