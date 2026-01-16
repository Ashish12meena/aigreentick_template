package com.aigreentick.services.template.model.contact;

import jakarta.persistence.*;
import lombok.Data;
import java.sql.Timestamp;

@Data
@Entity
@Table(name = "contact_attributes")
public class ContactAttributes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contact_id", nullable = false)
    private ChatContacts contact;

    @Column(name = "attribute", nullable = false, length = 255)
    private String attribute;

    @Column(name = "attribute_value", nullable = false, length = 255)
    private String attributeValue;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Timestamp updatedAt;
}
