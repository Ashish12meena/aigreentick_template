package com.aigreentick.services.template.model.template;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.aigreentick.services.template.enums.OtpTypes;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Entity
@Table(name = "template_component_buttons")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateComponentButton {
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id")
    private Long templateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    @JsonBackReference
    private TemplateComponent component;

    /**
     * Type of the button.
     * Allowed values: QUICK_REPLY, URL, PHONE_NUMBER, OTP
     */
    @Column(name = "type")
    private String type;

    /**
     * Type of OTP (if button type is OTP). Can be null for other button types.
     */
    @Column(name = "otp_type")
    @Enumerated(EnumType.STRING)
    private OtpTypes otpType;

    @Column(name = "number")
    private String number;

    @Column(name = "text")
    private String text;

    @Column(name = "url")
    private String url;

    /**
     * Index of the button within the component (used for ordering).
     */
    @Column(name = "button_index")
    private Integer buttonIndex;

    /**
     * Autofill text to pre-fill in the message for QUICK_REPLY buttons.
     */
    @Column(name = "autofill_text")
    private String autofillText;

    @Column(name = "example")
    List<String> example;

    @OneToMany(mappedBy = "button", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SupportedApp> supportedApps = new ArrayList<>();

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // ==================== HELPER METHODS ====================

    public void addSupportedApp(SupportedApp app) {
        supportedApps.add(app);
        app.setButton(this);
    }

    public void removeSupportedApp(SupportedApp app) {
        supportedApps.remove(app);
        app.setButton(null);
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(IST);
        this.createdAt = now;
        this.updatedAt = now;

        // Auto-populate templateId from parent chain
        if (this.templateId == null && this.component != null
                && this.component.getTemplate() != null
                && this.component.getTemplate().getId() != null) {
            this.templateId = this.component.getTemplate().getId();
        }
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