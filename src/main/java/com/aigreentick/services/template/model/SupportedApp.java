package com.aigreentick.services.template.model;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupportedApp {

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "signature_hash")
    private String signatureHash;
}

