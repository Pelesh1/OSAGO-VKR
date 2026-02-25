package vkr.osago.notifications;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vkr.osago.user.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/client/notifications")
public class ClientNotificationsController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;

    public ClientNotificationsController(JdbcTemplate jdbcTemplate, UserRepository users) {
        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
    }

    @GetMapping
    public List<NotificationDto> list(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "20") int limit
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query(
                """
                select id, type, title, message, body, is_read, created_at
                from insurance.notifications
                where recipient_id = ?
                order by created_at desc
                limit ?
                """,
                (rs, rowNum) -> new NotificationDto(
                        rs.getLong("id"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getString("body"),
                        rs.getBoolean("is_read"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                user.getId(),
                safeLimit
        );
    }

    @GetMapping("/unread-count")
    public UnreadCountDto unreadCount(@AuthenticationPrincipal UserDetails principal) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.notifications
                where recipient_id = ?
                  and is_read = false
                """,
                Long.class,
                user.getId()
        );
        return new UnreadCountDto(count == null ? 0 : count);
    }

    @PostMapping("/{id}/read")
    public MarkReadResponse markRead(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        int updated = jdbcTemplate.update(
                """
                update insurance.notifications
                set is_read = true,
                    read_at = now()
                where id = ?
                  and recipient_id = ?
                """,
                id,
                user.getId()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return new MarkReadResponse(id, true);
    }

    @PostMapping("/read-all")
    public ReadAllResponse readAll(@AuthenticationPrincipal UserDetails principal) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        int updated = jdbcTemplate.update(
                """
                update insurance.notifications
                set is_read = true,
                    read_at = now()
                where recipient_id = ?
                  and is_read = false
                """,
                user.getId()
        );
        return new ReadAllResponse(updated);
    }

    public record NotificationDto(
            Long id,
            String type,
            String title,
            String message,
            String body,
            Boolean isRead,
            OffsetDateTime createdAt
    ) {
    }

    public record UnreadCountDto(long unreadCount) {
    }

    public record MarkReadResponse(Long id, boolean isRead) {
    }

    public record ReadAllResponse(int updatedCount) {
    }
}
