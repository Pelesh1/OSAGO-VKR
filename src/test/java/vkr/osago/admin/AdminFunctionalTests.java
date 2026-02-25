package vkr.osago.admin;

import org.junit.jupiter.api.Test;
import vkr.osago.user.UserStatus;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AdminFunctionalTests {

    @Test
    void shouldCreateAgentAccount() {
        InMemoryUserStore store = new InMemoryUserStore();
        var cmd = new AdminWorkflow.CreateAgentCommand(
                UserStatus.ADMIN,
                "agent.new@test.local",
                "hash",
                "Maria",
                "Ivanova",
                "Petrovna"
        );

        var created = AdminWorkflow.createAgent(cmd, store);
        assertNotNull(created.id());
        assertEquals(UserStatus.AGENT, created.status());
        assertEquals("agent.new@test.local", created.email());
    }

    @Test
    void shouldHandleConflictAndInvalidIdentifiers() {
        InMemoryUserStore store = new InMemoryUserStore();
        AdminWorkflow.createAgent(new AdminWorkflow.CreateAgentCommand(
                UserStatus.ADMIN, "agent@test.local", "hash", "A", "B", null
        ), store);

        assertThrows(IllegalStateException.class, () ->
                AdminWorkflow.createAgent(new AdminWorkflow.CreateAgentCommand(
                        UserStatus.ADMIN, "agent@test.local", "hash2", "A", "B", null
                ), store));

        assertThrows(IllegalArgumentException.class, () ->
                AdminWorkflow.changeUserStatus(new AdminWorkflow.ChangeStatusCommand(
                        UserStatus.ADMIN, -1L, UserStatus.CLIENT
                ), store));

        assertThrows(IllegalStateException.class, () ->
                AdminWorkflow.changeUserStatus(new AdminWorkflow.ChangeStatusCommand(
                        UserStatus.ADMIN, 999L, UserStatus.CLIENT
                ), store));
    }

    @Test
    void shouldChangeStatusesCorrectly() {
        InMemoryUserStore store = new InMemoryUserStore();
        var agent = AdminWorkflow.createAgent(new AdminWorkflow.CreateAgentCommand(
                UserStatus.ADMIN, "status@test.local", "hash", "I", "P", null
        ), store);

        var updated = AdminWorkflow.changeUserStatus(new AdminWorkflow.ChangeStatusCommand(
                UserStatus.ADMIN, agent.id(), UserStatus.CLIENT
        ), store);
        assertEquals(UserStatus.CLIENT, updated.status());

        var same = AdminWorkflow.changeUserStatus(new AdminWorkflow.ChangeStatusCommand(
                UserStatus.ADMIN, agent.id(), UserStatus.CLIENT
        ), store);
        assertEquals(UserStatus.CLIENT, same.status());
    }

    @Test
    void shouldDenyAdministrativeOperationsForNonAdminRoles() {
        InMemoryUserStore store = new InMemoryUserStore();

        assertThrows(SecurityException.class, () ->
                AdminWorkflow.createAgent(new AdminWorkflow.CreateAgentCommand(
                        UserStatus.CLIENT, "x@test.local", "hash", "A", "B", null
                ), store));

        assertThrows(SecurityException.class, () ->
                AdminWorkflow.changeUserStatus(new AdminWorkflow.ChangeStatusCommand(
                        UserStatus.AGENT, 1L, UserStatus.CLIENT
                ), store));
    }

    @Test
    void shouldCoverValidationBranches() {
        InMemoryUserStore store = new InMemoryUserStore();
        assertThrows(SecurityException.class, () -> AdminWorkflow.requireAdmin(UserStatus.CLIENT));
        assertThrows(SecurityException.class, () -> AdminWorkflow.requireAdmin(null));
        assertThrows(SecurityException.class, () -> AdminWorkflow.createAgent(null, store));
        assertThrows(IllegalArgumentException.class, () -> AdminWorkflow.createAgent(
                new AdminWorkflow.CreateAgentCommand(UserStatus.ADMIN, "bad", "hash", "A", "B", null), store));
        assertThrows(IllegalArgumentException.class, () -> AdminWorkflow.createAgent(
                new AdminWorkflow.CreateAgentCommand(UserStatus.ADMIN, "ok@test.local", "", "A", "B", null), store));
        assertThrows(IllegalArgumentException.class, () -> AdminWorkflow.createAgent(
                new AdminWorkflow.CreateAgentCommand(UserStatus.ADMIN, "ok2@test.local", "hash", "", "B", null), store));
        assertThrows(SecurityException.class, () -> AdminWorkflow.changeUserStatus(null, store));
        assertThrows(IllegalArgumentException.class, () -> AdminWorkflow.changeUserStatus(
                new AdminWorkflow.ChangeStatusCommand(UserStatus.ADMIN, 1L, null), store));
    }

    private static final class InMemoryUserStore implements AdminWorkflow.UserStore {
        private final AtomicLong ids = new AtomicLong(1);
        private final Map<Long, AdminWorkflow.AgentAccount> byId = new HashMap<>();
        private final Map<String, Long> idByEmail = new HashMap<>();

        @Override
        public boolean existsByEmail(String email) {
            return idByEmail.containsKey(email);
        }

        @Override
        public AdminWorkflow.AgentAccount save(AdminWorkflow.AgentAccount account, String passwordHash) {
            Long id = ids.getAndIncrement();
            var saved = new AdminWorkflow.AgentAccount(
                    id,
                    account.email(),
                    account.firstName(),
                    account.lastName(),
                    account.middleName(),
                    account.status(),
                    account.active(),
                    account.createdAt() == null ? OffsetDateTime.now() : account.createdAt()
            );
            byId.put(id, saved);
            idByEmail.put(saved.email(), id);
            return saved;
        }

        @Override
        public AdminWorkflow.AgentAccount findById(Long id) {
            return byId.get(id);
        }

        @Override
        public AdminWorkflow.AgentAccount updateStatus(Long id, UserStatus newStatus) {
            var current = byId.get(id);
            if (current == null) return null;
            var updated = new AdminWorkflow.AgentAccount(
                    current.id(),
                    current.email(),
                    current.firstName(),
                    current.lastName(),
                    current.middleName(),
                    newStatus,
                    current.active(),
                    current.createdAt()
            );
            byId.put(id, updated);
            return updated;
        }
    }
}
