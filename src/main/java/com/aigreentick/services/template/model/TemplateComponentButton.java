package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.aigreentick.services.template.enums.OtpTypes;

@Entity
@Table(name = "template_component_buttons")
@Data
@Builder
public class TemplateComponentButton {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "BIGINT UNSIGNED")
    private Long id;

    @Column(name = "template_id")
    private Long templateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_id", nullable = false)
    @ToString.Exclude
    private TemplateComponent component;

    /**
     * Type of the button.
     * Allowed values: QUICK_REPLY, URL, PHONE_NUMBER, OTP
     */
    private String type;

    /**
     * Type of OTP (if button type is OTP). Can be null for other button types.
     */
    @Column(name = "otp_type")
    @Enumerated(EnumType.STRING)
    private OtpTypes otpType; // new

    private String number;

    private String text;

    private String url;

    /**
     * Index of the button within the component (used for ordering).
     */
    @Column(name = "button_index")
    private Integer buttonIndex; // new

    /**
     * Autofill text to pre-fill in the message for QUICK_REPLY buttons.
     */
    @Column(name = "autofill_text")
    private String autofillText; // new

    List<String> example;// new

    @OneToMany(mappedBy = "button", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SupportedApp> supportedApps = new ArrayList<>(); // new

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
}
