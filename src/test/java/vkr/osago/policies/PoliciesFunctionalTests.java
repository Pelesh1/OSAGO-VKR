package vkr.osago.policies;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.User;
import vkr.osago.user.UserEntity;
import vkr.osago.user.UserRepository;
import vkr.osago.user.UserStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PoliciesFunctionalTests {

    @Test
    void createApplicationShouldValidateValidAndInvalidData() {
        var valid = new PolicyWorkflow.PolicyApplicationData(
                10L,
                "Audi",
                "A123BC196",
                "Ivan Petrov",
                LocalDate.now().plusDays(1),
                true,
                true
        );
        var validErrors = PolicyWorkflow.validateCreateRequest(valid);
        assertTrue(validErrors.isEmpty());

        var invalid = new PolicyWorkflow.PolicyApplicationData(
                null,
                " ",
                null,
                "",
                LocalDate.now().minusDays(1),
                false,
                null
        );
        var invalidErrors = PolicyWorkflow.validateCreateRequest(invalid);
        assertTrue(invalidErrors.size() >= 4);
    }

    @Test
    void statusTransitionsShouldBeCorrect() {
        var status = PolicyWorkflow.nextStatus(
                PolicyWorkflow.PolicyApplicationStatus.NEW,
                PolicyWorkflow.PolicyAction.TAKE_IN_REVIEW
        );
        assertEquals(PolicyWorkflow.PolicyApplicationStatus.IN_REVIEW, status);

        status = PolicyWorkflow.nextStatus(
                PolicyWorkflow.PolicyApplicationStatus.IN_REVIEW,
                PolicyWorkflow.PolicyAction.APPROVE
        );
        assertEquals(PolicyWorkflow.PolicyApplicationStatus.APPROVED, status);

        status = PolicyWorkflow.nextStatus(
                PolicyWorkflow.PolicyApplicationStatus.APPROVED,
                PolicyWorkflow.PolicyAction.PAY
        );
        assertEquals(PolicyWorkflow.PolicyApplicationStatus.PAYMENT_PENDING, status);

        status = PolicyWorkflow.nextStatus(
                PolicyWorkflow.PolicyApplicationStatus.PAYMENT_PENDING,
                PolicyWorkflow.PolicyAction.CONFIRM_PAYMENT
        );
        assertEquals(PolicyWorkflow.PolicyApplicationStatus.PAID, status);

        assertThrows(IllegalArgumentException.class, () ->
                PolicyWorkflow.nextStatus(
                        PolicyWorkflow.PolicyApplicationStatus.PAID,
                        PolicyWorkflow.PolicyAction.APPROVE
                )
        );
    }

    @Test
    void deleteDraftShouldBeAllowedOnlyForPermittedStatuses() {
        assertTrue(PolicyWorkflow.canDeleteDraft(
                PolicyWorkflow.PolicyApplicationStatus.NEW,
                PolicyWorkflow.PolicyStatus.DRAFT
        ));
        assertTrue(PolicyWorkflow.canDeleteDraft(
                PolicyWorkflow.PolicyApplicationStatus.APPROVED,
                PolicyWorkflow.PolicyStatus.PENDING_PAY
        ));
        assertFalse(PolicyWorkflow.canDeleteDraft(
                PolicyWorkflow.PolicyApplicationStatus.PAID,
                PolicyWorkflow.PolicyStatus.ACTIVE
        ));
        assertFalse(PolicyWorkflow.canDeleteDraft(
                PolicyWorkflow.PolicyApplicationStatus.REJECTED,
                null
        ));
    }

    @Test
    void listPoliciesShouldReturnClientItems() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserRepository users = mock(UserRepository.class);

        UserEntity user = new UserEntity();
        user.setId(10L);
        user.setEmail("client@test.local");
        user.setFirstName("Ivan");
        user.setLastName("Petrov");
        user.setStatus(UserStatus.CLIENT);
        user.setSelfRegistered(true);
        user.setPasswordHash("x");

        when(users.findByEmail("client@test.local")).thenReturn(Optional.of(user));

        Object staleRef = createStaleAppRef(111L, 222L);
        @SuppressWarnings("unchecked")
        List<ClientPoliciesController.PolicyDto> staleList = (List<ClientPoliciesController.PolicyDto>) (List<?>) List.of(staleRef);
        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from insurance.policy_applications")),
                ArgumentMatchers.<RowMapper<ClientPoliciesController.PolicyDto>>any(),
                eq(10L)
        )).thenReturn(staleList);

        var policy = new ClientPoliciesController.PolicyDto(
                1L,
                "EEE 000000001",
                "OSAGO",
                "ACTIVE",
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                new BigDecimal("10000.00"),
                OffsetDateTime.now(),
                "Audi",
                "A4",
                "A123BC196"
        );

        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from insurance.policies")),
                ArgumentMatchers.<RowMapper<ClientPoliciesController.PolicyDto>>any(),
                eq(10L)
        )).thenReturn(List.of(policy));

        ClientPoliciesController controller = new ClientPoliciesController(jdbcTemplate, users);
        var principal = new User("client@test.local", "x", List.of());
        var result = controller.myPolicies(principal);

        assertEquals(1, result.size());
        assertEquals("EEE 000000001", result.get(0).number());

        verify(jdbcTemplate, atLeastOnce()).update(
                startsWith("delete from insurance.policy_applications"),
                eq(111L),
                eq(10L)
        );
        verify(jdbcTemplate, atLeastOnce()).update(
                contains("delete from insurance.policies"),
                eq(222L),
                eq(10L)
        );
    }

    @Test
    void policyDetailsShouldReturnCardOrNotFound() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserRepository users = mock(UserRepository.class);

        UserEntity user = new UserEntity();
        user.setId(5L);
        user.setEmail("client@test.local");
        user.setFirstName("Ivan");
        user.setLastName("Petrov");
        user.setStatus(UserStatus.CLIENT);
        user.setSelfRegistered(true);
        user.setPasswordHash("x");

        when(users.findByEmail("client@test.local")).thenReturn(Optional.of(user));

        var detail = new ClientPoliciesController.PolicyDetailDto(
                7L,
                "EEE 000000007",
                "OSAGO",
                "ACTIVE",
                LocalDate.now(),
                LocalDate.now().plusDays(365),
                new BigDecimal("12500.00"),
                OffsetDateTime.now(),
                1L,
                "Passenger",
                77L,
                "Moscow",
                150,
                false,
                12,
                "Audi",
                "A3",
                "VIN123",
                "A123BC196"
        );

        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from insurance.policies")),
                ArgumentMatchers.<RowMapper<ClientPoliciesController.PolicyDetailDto>>any(),
                eq(7L),
                eq(5L)
        )).thenReturn(List.of(detail));

        ClientPoliciesController controller = new ClientPoliciesController(jdbcTemplate, users);
        var principal = new User("client@test.local", "x", List.of());

        var result = controller.policyById(principal, 7L);
        assertEquals("EEE 000000007", result.number());

        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from insurance.policies")),
                ArgumentMatchers.<RowMapper<ClientPoliciesController.PolicyDetailDto>>any(),
                eq(999L),
                eq(5L)
        )).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> controller.policyById(principal, 999L));
    }

    @Test
    void policyPdfShouldBeGenerated() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UserRepository users = mock(UserRepository.class);

        UserEntity user = new UserEntity();
        user.setId(5L);
        user.setEmail("client@test.local");
        user.setFirstName("Ivan");
        user.setLastName("Petrov");
        user.setStatus(UserStatus.CLIENT);
        user.setSelfRegistered(true);
        user.setPasswordHash("x");

        when(users.findByEmail("client@test.local")).thenReturn(Optional.of(user));

        var detail = new ClientPoliciesController.PolicyDetailDto(
                7L,
                null,
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.now(),
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null
        );

        when(jdbcTemplate.query(
                argThat(sql -> sql != null && sql.contains("from insurance.policies")),
                ArgumentMatchers.<RowMapper<ClientPoliciesController.PolicyDetailDto>>any(),
                eq(7L),
                eq(5L)
        )).thenReturn(List.of(detail));

        ClientPoliciesController controller = new ClientPoliciesController(jdbcTemplate, users);
        var principal = new User("client@test.local", "x", List.of());
        var response = controller.policyPdf(principal, 7L);

        assertNotNull(response.getBody());
        String pdfHeader = new String(response.getBody(), 0, 4);
        assertEquals("%PDF", pdfHeader);
        assertTrue(response.getHeaders().getContentType().toString().contains("application/pdf"));
    }

    private Object createStaleAppRef(Long applicationId, Long policyId) {
        try {
            Class<?> refClass = Class.forName("vkr.osago.policies.ClientPoliciesController$StaleAppRef");
            var ctor = refClass.getDeclaredConstructor(Long.class, Long.class);
            ctor.setAccessible(true);
            return ctor.newInstance(applicationId, policyId);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
