package com.aigreentick.services.template.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ================= CORE RELATIONS (IDs ONLY) ================= */

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "created_by")
    private Integer createdBy;

    @Column(name = "country_id")
    private Long countryId;

    /* ================= BASIC INFO ================= */

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "mobile", nullable = false, length = 255)
    private String mobile;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "profile_photo", length = 522)
    private String profilePhoto;

    @Column(name = "rememberToken", length = 255)
    private String rememberToken;

    @Column(name = "api_token", length = 255)
    private String apiToken;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "city", length = 255)
    private String city;

    /* ================= BILLING ================= */

    @Column(name = "market_msg_charge")
    private Double marketMsgCharge;

    @Column(name = "utilty_msg_charge", nullable = false)
    @Builder.Default
    private Double utiltyMsgCharge = 0.0;

    @Column(name = "auth_msg_charge", nullable = false)
    @Builder.Default
    private Double authMsgCharge = 0.0;

    @Column(name = "balance", nullable = false)
    @Builder.Default
    private Double balance = 0.0;

    @Column(name = "balance_enabled", nullable = false)
    @Builder.Default
    private Boolean balanceEnabled = true;

    /* ================= STATUS FLAGS ================= */

    @Column(name = "online_status", nullable = false,columnDefinition = "enum('0','1')")
    @Builder.Default
    private String onlineStatus = "0";

    @Column(name = "agent_id")
    private Integer agentId;

    @Column(name = "credit", nullable = false)
    @Builder.Default
    private Double credit = 0.0;

    @Column(name = "debit")
    @Builder.Default
    private Double debit = 0.0;

    @Column(name = "status", nullable = false, columnDefinition = "enum('1','0','2') COMMENT '1 is active | 0 is deactive | 2 is ban'")
    private String status;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "logo", length = 255)
    private String logo;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_demo", nullable = false)
    @Builder.Default
    private DemoStatus isDemo = DemoStatus.on;

    @Column(name = "demo_end")
    private LocalDateTime demoEnd;

    /* ================= TOKENS ================= */

    @Column(name = "webhook_token", length = 255)
    private String webhookToken;

    @Column(name = "fcmAndroidToken", length = 255)
    private String fcmAndroidToken;

    @Column(name = "fcmIosToken", length = 255)
    private String fcmIosToken;

    @Column(name = "reset_token", length = 255)
    private String resetToken;

    @Column(name = "reset_token_expires")
    private LocalDateTime resetTokenExpires;

    @Column(name = "account_admin_id")
    private Long accountAdminId;

    /* ================= TIMESTAMPS ================= */

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /* ================= ENUMS (PIN-TO-PIN) ================= */

    public enum DemoStatus {
        on, off
    }
}