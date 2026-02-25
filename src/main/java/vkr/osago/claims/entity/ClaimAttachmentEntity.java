package vkr.osago.claims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "claim_attachments", schema = "insurance")
public class ClaimAttachmentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "attachment_type", nullable = false)
    private String attachmentType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getClaimId() { return claimId; }
    public void setClaimId(Long claimId) { this.claimId = claimId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public String getAttachmentType() { return attachmentType; }
    public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }
}
