package vkr.osago.claims.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vkr.osago.claims.dto.ApproveRequest;
import vkr.osago.claims.dto.CloseRequest;
import vkr.osago.claims.dto.RejectRequest;
import vkr.osago.claims.entity.ClaimEntity;
import vkr.osago.claims.entity.ClaimStatus;
import vkr.osago.claims.repo.ClaimRepository;

import java.time.OffsetDateTime;

@Service
public class ClaimService {

    private final ClaimRepository claimRepository;

    public ClaimService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    private ClaimEntity getOrThrow(Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
    }

    @Transactional
    public ClaimEntity approve(Long id, ApproveRequest req) {
        if (req.approvedAmount() == null || req.approvedAmount().signum() <= 0) {
            throw new IllegalArgumentException("approvedAmount must be > 0");
        }

        var claim = getOrThrow(id);

        // разрешаем одобрять только из этих статусов
        if (!(claim.getStatus() == ClaimStatus.NEW
                || claim.getStatus() == ClaimStatus.IN_REVIEW
                || claim.getStatus() == ClaimStatus.NEED_INFO)) {
            throw new IllegalStateException("Cannot approve claim in status: " + claim.getStatus());
        }

        claim.setStatus(ClaimStatus.APPROVED);
        claim.setApprovedAmount(req.approvedAmount());
        claim.setDecisionComment(req.decisionComment());
        claim.setDecidedAt(OffsetDateTime.now());
        claim.setPaidAt(null);

        return claimRepository.save(claim);
    }

    @Transactional
    public ClaimEntity reject(Long id, RejectRequest req) {
        if (req.decisionComment() == null || req.decisionComment().isBlank()) {
            throw new IllegalArgumentException("decisionComment is required");
        }

        var claim = getOrThrow(id);

        if (!(claim.getStatus() == ClaimStatus.NEW
                || claim.getStatus() == ClaimStatus.IN_REVIEW
                || claim.getStatus() == ClaimStatus.NEED_INFO)) {
            throw new IllegalStateException("Cannot reject claim in status: " + claim.getStatus());
        }

        claim.setStatus(ClaimStatus.REJECTED);
        claim.setApprovedAmount(null);
        claim.setDecisionComment(req.decisionComment());
        claim.setDecidedAt(OffsetDateTime.now());
        claim.setPaidAt(null);

        return claimRepository.save(claim);
    }

    @Transactional
    public ClaimEntity close(Long id, CloseRequest req) {
        var claim = getOrThrow(id);

        // закрывать можно только после решения
        if (!(claim.getStatus() == ClaimStatus.APPROVED || claim.getStatus() == ClaimStatus.REJECTED)) {
            throw new IllegalStateException("Cannot close claim in status: " + claim.getStatus());
        }

        claim.setStatus(ClaimStatus.CLOSED);

        if (req != null && req.paid()) {
            var paidAt = req.paidAt() != null ? req.paidAt() : OffsetDateTime.now();
            claim.setPaidAt(paidAt);
        } else {
            claim.setPaidAt(null);
        }

        return claimRepository.save(claim);
    }
}