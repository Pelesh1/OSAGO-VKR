package vkr.osago.claims.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "claims", schema = "insurance")
public class ClaimEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "number")
    private String number;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "policy_id")
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "insurance.claim_status")
    private ClaimStatus status;

    @Column(name = "description")
    private String description;

    @Column(name = "assigned_agent_id")
    private Long assignedAgentId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "accident_type", nullable = false, columnDefinition = "insurance.accident_type")
    private AccidentType accidentType;

    @Column(name = "accident_at", nullable = false)
    private OffsetDateTime accidentAt;

    @Column(name = "accident_place", nullable = false)
    private String accidentPlace;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "consent_personal_data", nullable = false)
    private boolean consentPersonalData;

    @Column(name = "consent_accuracy", nullable = false)
    private boolean consentAccuracy;

    // решение / выплата (то, что ты добавлял)
    @Column(name = "approved_amount")
    private BigDecimal approvedAmount;

    @Column(name = "decision_comment")
    private String decisionComment;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    // --- getters/setters (IntelliJ: Alt+Insert → Getter and Setter) ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getPolicyId() { return policyId; }
    public void setPolicyId(Long policyId) { this.policyId = policyId; }

    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getAssignedAgentId() { return assignedAgentId; }
    public void setAssignedAgentId(Long assignedAgentId) { this.assignedAgentId = assignedAgentId; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public AccidentType getAccidentType() { return accidentType; }
    public void setAccidentType(AccidentType accidentType) { this.accidentType = accidentType; }

    public OffsetDateTime getAccidentAt() { return accidentAt; }
    public void setAccidentAt(OffsetDateTime accidentAt) { this.accidentAt = accidentAt; }

    public String getAccidentPlace() { return accidentPlace; }
    public void setAccidentPlace(String accidentPlace) { this.accidentPlace = accidentPlace; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public boolean isConsentPersonalData() { return consentPersonalData; }
    public void setConsentPersonalData(boolean consentPersonalData) { this.consentPersonalData = consentPersonalData; }

    public boolean isConsentAccuracy() { return consentAccuracy; }
    public void setConsentAccuracy(boolean consentAccuracy) { this.consentAccuracy = consentAccuracy; }

    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }

    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }

    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }

    public OffsetDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(OffsetDateTime paidAt) { this.paidAt = paidAt; }
}
