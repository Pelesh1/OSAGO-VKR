package vkr.osago.claims.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import vkr.osago.claims.entity.ClaimAttachmentEntity;

import java.util.List;
import java.util.Optional;

public interface ClaimAttachmentRepository extends JpaRepository<ClaimAttachmentEntity, Long> {
    List<ClaimAttachmentEntity> findAllByClaimIdOrderByCreatedAtDesc(Long claimId);
    Optional<ClaimAttachmentEntity> findByIdAndClaimId(Long id, Long claimId);
}
