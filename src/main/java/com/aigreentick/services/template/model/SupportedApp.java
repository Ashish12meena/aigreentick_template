package com.aigreentick.services.template.model;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// important note:- it need ids to be bigint ussigned but it create them as signed so table of this should be created by mysql script

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "supported_apps")
public class SupportedApp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "button_id", nullable = false)
    private TemplateComponentButton button; // new 

    @Column(name = "package_name")
    private String packageName;

    @Column(name = "signature_hash")
    private String signatureHash;

    /**
     * Override equals/hashCode to prevent issues with bidirectional relationships
     * Don't include 'button' in equals/hashCode to avoid circular references
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SupportedApp))
            return false;
        SupportedApp that = (SupportedApp) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(packageName, that.packageName) &&
                Objects.equals(signatureHash, that.signatureHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, packageName, signatureHash);
    }

    /**
     * Override toString to prevent infinite loops with bidirectional relationships
     */
    @Override
    public String toString() {
        return "SupportedApp{" +
                "id=" + id +
                ", packageName='" + packageName + '\'' +
                ", signatureHash='" + signatureHash + '\'' +
                '}';
    }
}
