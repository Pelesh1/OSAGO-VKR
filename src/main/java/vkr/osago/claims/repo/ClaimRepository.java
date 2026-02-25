package vkr.osago.claims.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vkr.osago.claims.entity.ClaimEntity;
import vkr.osago.claims.entity.ClaimStatus;

import java.util.List;
import java.util.Optional;

public interface ClaimRepository extends JpaRepository<ClaimEntity, Long> {
    Page<ClaimEntity> findAllByStatus(ClaimStatus status, Pageable pageable);
    Page<ClaimEntity> findAllByAssignedAgentId(Long assignedAgentId, Pageable pageable);
    Page<ClaimEntity> findAllByAssignedAgentIdAndStatus(Long assignedAgentId, ClaimStatus status, Pageable pageable);
    Page<ClaimEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Optional<ClaimEntity> findByIdAndUserId(Long id, Long userId);
    long countByUserId(Long userId);
    long countByUserIdAndStatusIn(Long userId, List<ClaimStatus> statuses);
}
