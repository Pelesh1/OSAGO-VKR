package vkr.osago.admin;

import vkr.osago.user.UserStatus;

import java.time.OffsetDateTime;
import java.util.Locale;

public final class AdminWorkflow {
    private AdminWorkflow() {
    }

    public static AgentAccount createAgent(CreateAgentCommand cmd, UserStore store) {
        requireAdmin(cmd == null ? null : cmd.actorRole());
        if (cmd == null) throw new IllegalArgumentException("command is required");
        if (store == null) throw new IllegalArgumentException("store is required");
        if (isBlank(cmd.email()) || !cmd.email().contains("@")) {
            throw new IllegalArgumentException("email is invalid");
        }
        if (isBlank(cmd.passwordHash())) {
            throw new IllegalArgumentException("passwordHash is required");
        }
        if (isBlank(cmd.firstName()) || isBlank(cmd.lastName())) {
            throw new IllegalArgumentException("firstName and lastName are required");
        }
        String normalizedEmail = cmd.email().trim().toLowerCase(Locale.ROOT);
        if (store.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("Agent with this email already exists");
        }

        AgentAccount account = new AgentAccount(
                null,
                normalizedEmail,
                cmd.firstName().trim(),
                cmd.lastName().trim(),
                trimToNull(cmd.middleName()),
                UserStatus.AGENT,
                true,
                OffsetDateTime.now()
        );
        return store.save(account, cmd.passwordHash());
    }

    public static AgentAccount changeUserStatus(ChangeStatusCommand cmd, UserStore store) {
        requireAdmin(cmd == null ? null : cmd.actorRole());
        if (cmd == null) throw new IllegalArgumentException("command is required");
        if (store == null) throw new IllegalArgumentException("store is required");
        if (cmd.userId() == null || cmd.userId() <= 0) {
            throw new IllegalArgumentException("userId is invalid");
        }
        if (cmd.newStatus() == null) {
            throw new IllegalArgumentException("newStatus is required");
        }

        AgentAccount current = store.findById(cmd.userId());
        if (current == null) {
            throw new IllegalStateException("User not found");
        }
        if (current.status() == cmd.newStatus()) {
            return current;
        }
        AgentAccount updated = new AgentAccount(
                current.id(),
                current.email(),
                current.firstName(),
                current.lastName(),
                current.middleName(),
                cmd.newStatus(),
                current.active(),
                current.createdAt()
        );
        return store.updateStatus(updated.id(), updated.status());
    }

    public static void requireAdmin(UserStatus role) {
        if (role != UserStatus.ADMIN) {
            throw new SecurityException("Access denied");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface UserStore {
        boolean existsByEmail(String email);

        AgentAccount save(AgentAccount account, String passwordHash);

        AgentAccount findById(Long id);

        AgentAccount updateStatus(Long id, UserStatus newStatus);
    }

    public record CreateAgentCommand(
            UserStatus actorRole,
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String middleName
    ) {
    }

    public record ChangeStatusCommand(
            UserStatus actorRole,
            Long userId,
            UserStatus newStatus
    ) {
    }

    public record AgentAccount(
            Long id,
            String email,
            String firstName,
            String lastName,
            String middleName,
            UserStatus status,
            boolean active,
            OffsetDateTime createdAt
    ) {
    }
}
