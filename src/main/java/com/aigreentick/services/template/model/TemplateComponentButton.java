package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

import com.aigreentick.services.template.enums.OtpTypes;

@Entity
@Table(name = "template_component_buttons")
@Data
public class TemplateComponentButton {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "component_id")
    private Integer componentId;

    /**
     * Type of the button.
     * Allowed values: QUICK_REPLY, URL, PHONE_NUMBER, OTP
     */
    private String type;

    /**
     * Type of OTP (if button type is OTP). Can be null for other button types.
     */
    @Column(name = "otp_type")
    private OtpTypes otpType; // new

    private String number;

    private String text;

    private String url;

    /**
     * Index of the button within the component (used for ordering).
     */
    private int index; // new

    /**
     * Autofill text to pre-fill in the message for QUICK_REPLY buttons.
     */
    @Column(name = "autofill_text")
    private String autofillText; // new

    List<String> example;// new

    @OneToMany(mappedBy = "button", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SupportedApp> supportedApps;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
