package vkr.osago.claims;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vkr.osago.claims.dto.ApproveRequest;
import vkr.osago.claims.dto.CloseRequest;
import vkr.osago.claims.dto.RejectRequest;
import vkr.osago.claims.entity.AccidentType;
import vkr.osago.claims.entity.ClaimEntity;
import vkr.osago.claims.entity.ClaimStatus;
import vkr.osago.claims.repo.ClaimRepository;
import vkr.osago.claims.service.ClaimService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClaimsFunctionalTests {

    @Test
    void createClaimShouldValidateValidAndInvalidData() {
        var valid = new ClaimWorkflow.ClaimCreateData(
                1L,
                "Accident description is valid",
                AccidentType.COLLISION,
                OffsetDateTime.now().minusDays(1),
                "City, Street 1",
                "+7 900 123-45-67",
                "a@b.com",
                true,
                true
        );
        assertTrue(ClaimWorkflow.validateCreate(valid).isEmpty());

        var invalid = new ClaimWorkflow.ClaimCreateData(
                null,
                "short",
                null,
                null,
                " ",
                "12",
                null,
                false,
                null
        );
        assertFalse(ClaimWorkflow.validateCreate(invalid).isEmpty());

        assertFalse(ClaimWorkflow.validateCreate(null).isEmpty());
    }

    @Test
    void attachmentRulesShouldWork() {
        assertTrue(ClaimWorkflow.validateAttachment(ClaimStatus.NEW, "file.pdf", 1024).isEmpty());
        assertFalse(ClaimWorkflow.validateAttachment(ClaimStatus.REJECTED, "file.pdf", 1024).isEmpty());
        assertFalse(ClaimWorkflow.validateAttachment(ClaimStatus.NEW, "", 1024).isEmpty());
        assertFalse(ClaimWorkflow.validateAttachment(ClaimStatus.NEW, "file.pdf", 0).isEmpty());
        assertFalse(ClaimWorkflow.validateAttachment(ClaimStatus.CLOSED, "file.pdf", 10).isEmpty());
        assertFalse(ClaimWorkflow.validateAttachment(ClaimStatus.NEW, "file.pdf", 21L * 1024L * 1024L).isEmpty());
    }

    @Test
    void statusTransitionsShouldBeCorrect() {
        assertEquals(
                ClaimStatus.IN_REVIEW,
                ClaimWorkflow.nextStatus(ClaimStatus.NEW, ClaimWorkflow.ClaimAction.TAKE_IN_REVIEW)
        );
        assertEquals(
                ClaimStatus.REJECTED,
                ClaimWorkflow.nextStatus(ClaimStatus.NEW, ClaimWorkflow.ClaimAction.REJECT)
        );
        assertEquals(
                ClaimStatus.NEED_INFO,
                ClaimWorkflow.nextStatus(ClaimStatus.IN_REVIEW, ClaimWorkflow.ClaimAction.REQUEST_INFO)
        );
        assertEquals(
                ClaimStatus.IN_REVIEW,
                ClaimWorkflow.nextStatus(ClaimStatus.NEED_INFO, ClaimWorkflow.ClaimAction.CLIENT_UPDATE)
        );
        assertEquals(
                ClaimStatus.APPROVED,
                ClaimWorkflow.nextStatus(ClaimStatus.IN_REVIEW, ClaimWorkflow.ClaimAction.APPROVE)
        );
        assertEquals(
                ClaimStatus.CLOSED,
                ClaimWorkflow.nextStatus(ClaimStatus.APPROVED, ClaimWorkflow.ClaimAction.CLOSE)
        );
        assertEquals(
                ClaimStatus.CLOSED,
                ClaimWorkflow.nextStatus(ClaimStatus.REJECTED, ClaimWorkflow.ClaimAction.CLOSE)
        );
        assertThrows(IllegalArgumentException.class, () ->
                ClaimWorkflow.nextStatus(ClaimStatus.CLOSED, ClaimWorkflow.ClaimAction.APPROVE)
        );
    }

    @Test
    void decisionValidationShouldWork() {
        assertThrows(IllegalArgumentException.class, () ->
                ClaimWorkflow.validateDecision(
                        ClaimWorkflow.ClaimAction.APPROVE,
                        new ClaimWorkflow.Decision(new BigDecimal("0"), "ok")
                )
        );
        assertThrows(IllegalArgumentException.class, () ->
                ClaimWorkflow.validateDecision(
                        ClaimWorkflow.ClaimAction.REJECT,
                        new ClaimWorkflow.Decision(null, " ")
                )
        );
        var ok = ClaimWorkflow.validateDecision(
                ClaimWorkflow.ClaimAction.APPROVE,
                new ClaimWorkflow.Decision(new BigDecimal("1000.00"), "ok")
        );
        assertNotNull(ok);

        var rejectOk = ClaimWorkflow.validateDecision(
                ClaimWorkflow.ClaimAction.REJECT,
                new ClaimWorkflow.Decision(null, "reason")
        );
        assertNotNull(rejectOk);

        assertNull(ClaimWorkflow.validateDecision(ClaimWorkflow.ClaimAction.CLOSE, null));
    }

    @Test
    void closedOrRejectedShouldBlockOperations() {
        assertFalse(ClaimWorkflow.canOperate(ClaimStatus.CLOSED));
        assertFalse(ClaimWorkflow.canOperate(ClaimStatus.REJECTED));
        assertTrue(ClaimWorkflow.canOperate(ClaimStatus.IN_REVIEW));
    }

    @Test
    void historyEntryShouldCaptureStatusChange() {
        var entry = ClaimWorkflow.history("NEW", "IN_REVIEW", "taken", 10L);
        assertEquals("NEW", entry.oldStatus());
        assertEquals("IN_REVIEW", entry.newStatus());
        assertEquals("taken", entry.comment());
        assertEquals(10L, entry.changedByUserId());
        assertNotNull(entry.createdAt());
    }

    @Test
    void claimServiceShouldApproveRejectAndClose() {
        ClaimRepository repo = mock(ClaimRepository.class);
        ClaimService service = new ClaimService(repo);

        ClaimEntity claim = new ClaimEntity();
        claim.setId(1L);
        claim.setStatus(ClaimStatus.NEW);

        when(repo.findById(1L)).thenReturn(Optional.of(claim));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var approved = service.approve(1L, new ApproveRequest(new BigDecimal("5000.00"), "ok"));
        assertEquals(ClaimStatus.APPROVED, approved.getStatus());
        assertEquals(new BigDecimal("5000.00"), approved.getApprovedAmount());

        claim.setStatus(ClaimStatus.IN_REVIEW);
        var rejected = service.reject(1L, new RejectRequest("no"));
        assertEquals(ClaimStatus.REJECTED, rejected.getStatus());

        claim.setStatus(ClaimStatus.APPROVED);
        var closed = service.close(1L, new CloseRequest(true, OffsetDateTime.now()));
        assertEquals(ClaimStatus.CLOSED, closed.getStatus());
        assertNotNull(closed.getPaidAt());

        claim.setStatus(ClaimStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> service.reject(1L, new RejectRequest("no")));

        ArgumentCaptor<ClaimEntity> captor = ArgumentCaptor.forClass(ClaimEntity.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        assertFalse(captor.getAllValues().isEmpty());
    }
}
