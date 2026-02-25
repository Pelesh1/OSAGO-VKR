package vkr.osago.claims.controller;

import vkr.osago.claims.dto.ApproveRequest;
import vkr.osago.claims.dto.CloseRequest;
import vkr.osago.claims.dto.RejectRequest;
import vkr.osago.claims.entity.ClaimEntity;
import vkr.osago.claims.entity.ClaimStatus;
import vkr.osago.claims.repo.ClaimRepository;
import vkr.osago.claims.service.ClaimService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimRepository claimRepository;
    private final ClaimService claimService;

    public ClaimController(ClaimRepository claimRepository, ClaimService claimService) {
        this.claimRepository = claimRepository;
        this.claimService = claimService;
    }

    @GetMapping
    public Page<ClaimEntity> list(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) Long assignedAgentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size);

        if (status != null && assignedAgentId != null) {
            return claimRepository.findAllByAssignedAgentIdAndStatus(assignedAgentId, status, pageable);
        }
        if (status != null) {
            return claimRepository.findAllByStatus(status, pageable);
        }
        if (assignedAgentId != null) {
            return claimRepository.findAllByAssignedAgentId(assignedAgentId, pageable);
        }
        return claimRepository.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ClaimEntity get(@PathVariable Long id) {
        return claimRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + id));
    }

    @PostMapping("/{id}/approve")
    public ClaimEntity approve(@PathVariable Long id, @RequestBody ApproveRequest req) {
        return claimService.approve(id, req);
    }

    @PostMapping("/{id}/reject")
    public ClaimEntity reject(@PathVariable Long id, @RequestBody RejectRequest req) {
        return claimService.reject(id, req);
    }

    @PostMapping("/{id}/close")
    public ClaimEntity close(@PathVariable Long id, @RequestBody(required = false) CloseRequest req) {
        return claimService.close(id, req);
    }
}
