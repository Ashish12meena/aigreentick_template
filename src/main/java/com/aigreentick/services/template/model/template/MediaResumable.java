package com.aigreentick.services.template.model.template;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "media_resumable")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaResumable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    // TEXT
    @Column(name = "session_id", columnDefinition = "TEXT")
    private String sessionId;

    // TEXT
    @Column(name = "media_handle", columnDefinition = "TEXT")
    private String mediaHandle;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "media_type")
    private String mediaType; // IMAGE, VIDEO, DOCUMENT, AUDIO

    @Column(name = "status")
    private String status; // PENDING, COMPLETED, FAILED

    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "waba_id")
    private String wabaId;

    // LONGTEXT for large JSON payloads
    @Column(name = "upload_response_json", columnDefinition = "TEXT")
    private String uploadResponseJson;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "is_chunked_upload")
    private Boolean isChunkedUpload;

    @Column(name = "file_offset")
    private Long fileOffset;
}
