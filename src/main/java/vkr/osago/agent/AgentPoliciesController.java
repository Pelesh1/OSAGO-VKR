package vkr.osago.agent;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vkr.osago.user.UserEntity;
import vkr.osago.user.UserRepository;
import vkr.osago.user.UserStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/agent/policies")
public class AgentPoliciesController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;

    public AgentPoliciesController(JdbcTemplate jdbcTemplate, UserRepository users) {
        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
    }

    @GetMapping
    public PoliciesPageResponse myPolicies(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UserEntity agent = requireAgent(principal);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = safePage * safeSize;

        StringBuilder where = new StringBuilder("""
                where (
                    p.agent_id = ?
                    or exists (
                        select 1
                        from insurance.policy_applications pa
                        where pa.issued_policy_id = p.id
                          and pa.assigned_agent_id = ?
                    )
                )
                """);
        List<Object> args = new ArrayList<>();
        args.add(agent.getId());
        args.add(agent.getId());

        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus != null) {
            where.append(" and upper(p.status::text) = ? ");
            args.add(normalizedStatus);
        }

        if (clientId != null) {
            where.append(" and p.user_id = ? ");
            args.add(clientId);
        }

        if (policyId != null) {
            where.append(" and p.id = ? ");
            args.add(policyId);
        }

        String normalizedQ = normalizeQuery(q);
        if (normalizedQ != null) {
            where.append("""
                     and (
                        lower(coalesce(p.number, '')) like ?
                        or lower(coalesce(v.reg_number, '')) like ?
                        or lower(coalesce(v.vin, '')) like ?
                        or lower(coalesce(v.brand, '')) like ?
                        or lower(coalesce(v.model, '')) like ?
                        or lower(
                            trim(
                                both ' ' from
                                coalesce(u.last_name, '') || ' ' ||
                                coalesce(u.first_name, '') || ' ' ||
                                coalesce(u.middle_name, '')
                            )
                        ) like ?
                    )
                    """);
            String like = "%" + normalizedQ + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }

        Long total = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.policies p
                join insurance.users u on u.id = p.user_id
                left join insurance.vehicles v on v.id = p.vehicle_id
                """ + where,
                Long.class,
                args.toArray()
        );
        long totalElements = total == null ? 0L : total;

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add(offset);
        List<AgentPolicyShortDto> items = jdbcTemplate.query(
                """
                select p.id,
                       p.number,
                       p.type::text as policy_type,
                       p.status::text as status,
                       p.start_date,
                       p.end_date,
                       p.created_at,
                       p.premium_amount,
                       p.user_id,
                       u.email as client_email,
                       u.first_name,
                       u.last_name,
                       u.middle_name,
                       v.brand,
                       v.model,
                       v.reg_number,
                       v.vin
                from insurance.policies p
                join insurance.users u on u.id = p.user_id
                left join insurance.vehicles v on v.id = p.vehicle_id
                """ + where + """
                order by p.created_at desc, p.id desc
                limit ? offset ?
                """,
                (rs, rowNum) -> new AgentPolicyShortDto(
                        rs.getLong("id"),
                        rs.getString("number"),
                        normalizeUpper(rs.getString("policy_type")),
                        normalizeUpper(rs.getString("status")),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getBigDecimal("premium_amount"),
                        rs.getLong("user_id"),
                        rs.getString("client_email"),
                        buildFio(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name")),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getString("reg_number"),
                        rs.getString("vin")
                ),
                listArgs.toArray()
        );

        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil((double) totalElements / (double) safeSize);
        return new PoliciesPageResponse(items, safePage, safeSize, totalElements, totalPages);
    }

    private UserEntity requireAgent(UserDetails principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        UserEntity user = users.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getStatus() != UserStatus.AGENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return user;
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeQuery(String value) {
        if (value == null) return null;
        String t = value.trim().toLowerCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    private String buildFio(String lastName, String firstName, String middleName) {
        StringBuilder sb = new StringBuilder();
        if (lastName != null && !lastName.isBlank()) sb.append(lastName.trim());
        if (firstName != null && !firstName.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(firstName.trim());
        }
        if (middleName != null && !middleName.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(middleName.trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private String normalizeUpper(String value) {
        if (value == null) return "";
        return value.toUpperCase(Locale.ROOT);
    }

    public record PoliciesPageResponse(
            List<AgentPolicyShortDto> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record AgentPolicyShortDto(
            Long id,
            String number,
            String policyType,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            OffsetDateTime createdAt,
            BigDecimal premiumAmount,
            Long userId,
            String clientEmail,
            String clientName,
            String brand,
            String model,
            String regNumber,
            String vin
    ) {
    }
}
