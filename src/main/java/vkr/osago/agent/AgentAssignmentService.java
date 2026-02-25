package vkr.osago.agent;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentAssignmentService {

    private final JdbcTemplate jdbcTemplate;

    public AgentAssignmentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Long ensureAgentAssignedToUser(Long userId) {
        Long existing = getUserAssignedAgentId(userId);
        if (existing != null) {
            return existing;
        }

        Long nextAgentId = findNextAgentId();
        if (nextAgentId == null) {
            return null;
        }

        jdbcTemplate.update(
                """
                update insurance.users
                set assigned_agent_id = ?
                where id = ?
                  and assigned_agent_id is null
                """,
                nextAgentId,
                userId
        );

        return getUserAssignedAgentId(userId);
    }

    private Long getUserAssignedAgentId(Long userId) {
        try {
            return jdbcTemplate.query(
                    "select assigned_agent_id from insurance.users where id = ? limit 1",
                    rs -> rs.next() ? (Long) rs.getObject(1) : null,
                    userId
            );
        } catch (DataAccessException ex) {
            return null;
        }
    }

    private Long findNextAgentId() {
        try {
            Long lastAssignedAgentId = jdbcTemplate.query(
                    """
                    select x.assigned_agent_id
                    from (
                        select assigned_agent_id, created_at
                        from insurance.policy_applications
                        where assigned_agent_id is not null
                        union all
                        select assigned_agent_id, created_at
                        from insurance.claims
                        where assigned_agent_id is not null
                    ) x
                    order by x.created_at desc
                    limit 1
                    """,
                    rs -> rs.next() ? (Long) rs.getObject(1) : null
            );

            if (lastAssignedAgentId != null) {
                Long nextAfterLast = jdbcTemplate.query(
                        """
                        select id
                        from insurance.users
                        where status = 'AGENT'
                          and id > ?
                        order by id
                        limit 1
                        """,
                        rs -> rs.next() ? rs.getLong(1) : null,
                        lastAssignedAgentId
                );
                if (nextAfterLast != null) {
                    return nextAfterLast;
                }
            }

            return jdbcTemplate.query(
                    """
                    select id
                    from insurance.users
                    where status = 'AGENT'
                    order by id
                    limit 1
                    """,
                    rs -> rs.next() ? rs.getLong(1) : null
            );
        } catch (DataAccessException ex) {
            return null;
        }
    }
}
