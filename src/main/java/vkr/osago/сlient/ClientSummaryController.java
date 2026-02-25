package vkr.osago.сlient;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vkr.osago.claims.entity.ClaimStatus;
import vkr.osago.claims.repo.ClaimRepository;
import vkr.osago.user.UserRepository;

import java.util.List;

@RestController
@RequestMapping("/api/client")
public class ClientSummaryController {

    private final UserRepository users;
    private final ClaimRepository claims;
    private final JdbcTemplate jdbcTemplate;

    public ClientSummaryController(UserRepository users, ClaimRepository claims, JdbcTemplate jdbcTemplate) {
        this.users = users;
        this.claims = claims;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/summary")
    public SummaryDto summary(@AuthenticationPrincipal UserDetails principal) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();

        long claimsTotal = claims.countByUserId(user.getId());
        long claimsInProgress = claims.countByUserIdAndStatusIn(
                user.getId(),
                List.of(ClaimStatus.NEW, ClaimStatus.IN_REVIEW, ClaimStatus.NEED_INFO)
        );

        Long activePoliciesCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.policies
                where user_id = ?
                  and status = 'ACTIVE'::insurance.policy_status
                """,
                Long.class,
                user.getId()
        );

        long policies = activePoliciesCount == null ? 0 : activePoliciesCount;
        Long assignedAgentId = jdbcTemplate.queryForObject(
                "select assigned_agent_id from insurance.users where id = ?",
                Long.class,
                user.getId()
        );

        String agentPhone = null;
        if (assignedAgentId != null) {
            List<String> phones = jdbcTemplate.query(
                    """
                    select ap.phone
                    from insurance.agent_profiles ap
                    where ap.user_id = ?
                    limit 1
                    """,
                    (rs, rowNum) -> rs.getString("phone"),
                    assignedAgentId
            );
            if (!phones.isEmpty()) {
                agentPhone = phones.get(0);
            }
        }

        return new SummaryDto(policies, claimsTotal, claimsInProgress, agentPhone);
    }

    public record SummaryDto(long policies, long claimsTotal, long claimsInProgress, String agentPhone) {
    }
}
