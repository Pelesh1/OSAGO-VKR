package vkr.osago.agent;

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
@RequestMapping("/api/agent")
public class AgentDashboardController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;

    public AgentDashboardController(JdbcTemplate jdbcTemplate, UserRepository users) {
        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
    }

    @GetMapping("/summary")
    public AgentSummaryDto summary(@AuthenticationPrincipal UserDetails principal) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();

        Long activePolicies = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.policies
                where agent_id = ?
                  and status = 'ACTIVE'::insurance.policy_status
                """,
                Long.class,
                user.getId()
        );

        Long pendingApplications = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.policy_applications
                where assigned_agent_id = ?
                  and upper(status) in ('NEW', 'IN_REVIEW', 'NEED_INFO', 'APPROVED', 'PAYMENT_PENDING')
                """,
                Long.class,
                user.getId()
        );

        Long claimsInReview = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.claims
                where assigned_agent_id = ?
                  and status::text in ('NEW', 'IN_REVIEW', 'NEED_INFO')
                """,
                Long.class,
                user.getId()
        );

        Long clientsTotal = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.users
                where status = 'CLIENT'
                  and assigned_agent_id = ?
                """,
                Long.class,
                user.getId()
        );

        Long completedToday = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.claims
                where assigned_agent_id = ?
                  and status::text in ('APPROVED', 'REJECTED', 'CLOSED')
                  and decided_at is not null
                  and decided_at::date = current_date
                """,
                Long.class,
                user.getId()
        );

        Long unreadNotifications = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.notifications
                where recipient_id = ?
                  and is_read = false
                """,
                Long.class,
                user.getId()
        );

        return new AgentSummaryDto(
                safeLong(activePolicies),
                safeLong(pendingApplications),
                safeLong(claimsInReview),
                safeLong(clientsTotal),
                safeLong(completedToday),
                safeLong(unreadNotifications)
        );
    }

    @GetMapping("/notifications")
    public List<AgentNotificationDto> notifications(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "10") int limit
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return jdbcTemplate.query(
                """
                select id, type, title, message, is_read, created_at
                from insurance.notifications
                where recipient_id = ?
                order by created_at desc
                limit ?
                """,
                (rs, rowNum) -> new AgentNotificationDto(
                        rs.getLong("id"),
                        rs.getString("type"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getBoolean("is_read"),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                user.getId(),
                safeLimit
        );
    }

    @PostMapping("/notifications/{id}/read")
    public ReadNotificationResponse markRead(
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
        return new ReadNotificationResponse(id, true);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    public record AgentSummaryDto(
            long activePolicies,
            long pendingApplications,
            long claimsInReview,
            long clientsTotal,
            long completedToday,
            long unreadNotifications
    ) {
    }

    public record AgentNotificationDto(
            Long id,
            String type,
            String title,
            String message,
            Boolean isRead,
            OffsetDateTime createdAt
    ) {
    }

    public record ReadNotificationResponse(Long id, boolean isRead) {
    }
}
