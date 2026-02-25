package vkr.osago.agent;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

@RestController
@RequestMapping("/api/agent/applications")
public class AgentPolicyApplicationsController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;

    public AgentPolicyApplicationsController(JdbcTemplate jdbcTemplate, UserRepository users) {
        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
    }

    @GetMapping
    public ApplicationsPageResponse myApplications(
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

        StringBuilder where = new StringBuilder(" where pa.assigned_agent_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(agent.getId());

        String normalizedStatus = normalizeStatus(status);
        if (normalizedStatus != null) {
            where.append(" and upper(pa.status) = ? ");
            args.add(normalizedStatus);
        }

        if (clientId != null) {
            where.append(" and pa.user_id = ? ");
            args.add(clientId);
        }

        if (policyId != null) {
            where.append(" and pa.issued_policy_id = ? ");
            args.add(policyId);
        }

        String normalizedQ = normalizeQuery(q);
        if (normalizedQ != null) {
            where.append("""
                     and (
                        cast(pa.id as text) like ?
                        or lower(coalesce(p.number, '')) like ?
                        or lower(coalesce(v.reg_number, '')) like ?
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
        }

        Long total = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.policy_applications pa
                join insurance.users u on u.id = pa.user_id
                left join insurance.policies p on p.id = pa.issued_policy_id
                left join insurance.vehicles v on v.id = pa.vehicle_id
                """ + where,
                Long.class,
                args.toArray()
        );
        long totalElements = total == null ? 0L : total;

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(safeSize);
        listArgs.add(offset);
        var items = jdbcTemplate.query(
                """
                select pa.id,
                       pa.status,
                       pa.comment,
                       pa.created_at,
                       pa.updated_at,
                       pa.user_id,
                       pa.issued_policy_id,
                       p.number as policy_number,
                       p.status::text as policy_status,
                       p.premium_amount,
                       u.email as client_email,
                       u.first_name,
                       u.last_name,
                       u.middle_name,
                       v.brand,
                       v.model,
                       v.reg_number,
                       v.vin
                from insurance.policy_applications pa
                join insurance.users u on u.id = pa.user_id
                left join insurance.policies p on p.id = pa.issued_policy_id
                left join insurance.vehicles v on v.id = pa.vehicle_id
                """ + where + """
                order by pa.created_at desc, pa.id desc
                limit ? offset ?
                """,
                (rs, rowNum) -> new AgentApplicationShortDto(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getString("comment"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getLong("user_id"),
                        (Long) rs.getObject("issued_policy_id"),
                        rs.getString("policy_number"),
                        rs.getString("policy_status"),
                        rs.getBigDecimal("premium_amount"),
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
        return new ApplicationsPageResponse(items, safePage, safeSize, totalElements, totalPages);
    }

    @GetMapping("/{id}")
    public AgentApplicationDetailsDto details(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id
    ) {
        UserEntity agent = requireAgent(principal);
        var rows = jdbcTemplate.query(
                """
                select pa.id,
                       pa.status,
                       pa.comment,
                       pa.created_at,
                       pa.updated_at,
                       pa.user_id,
                       pa.issued_policy_id,
                       p.number as policy_number,
                       p.status::text as policy_status,
                       p.type::text as policy_type,
                       p.start_date,
                       p.end_date,
                       p.premium_amount,
                       p.term_months,
                       p.power_hp,
                       p.unlimited_drivers,
                       rc.name as vehicle_category_name,
                       rr.name as region_name,
                       u.email as client_email,
                       u.first_name,
                       u.last_name,
                       u.middle_name,
                       v.brand,
                       v.model,
                       v.reg_number,
                       v.vin,
                       ip.birth_date,
                       ip.passport_series,
                       ip.passport_number,
                       ip.passport_issue_date,
                       ip.passport_issuer,
                       ip.registration_address,
                       di.driver_license_number,
                       di.license_issued_date
                from insurance.policy_applications pa
                join insurance.users u on u.id = pa.user_id
                left join insurance.policies p on p.id = pa.issued_policy_id
                left join insurance.vehicles v on v.id = pa.vehicle_id
                left join insurance.ref_vehicle_categories rc on rc.id = p.vehicle_category_id
                left join insurance.ref_regions rr on rr.id = p.region_id
                left join insurance.insured_person_profiles ip on ip.user_id = pa.user_id
                left join insurance.client_driver_info di on di.user_id = pa.user_id
                where pa.id = ?
                  and pa.assigned_agent_id = ?
                limit 1
                """,
                (rs, rowNum) -> new AgentApplicationDetailsDto(
                        rs.getLong("id"),
                        rs.getString("status"),
                        rs.getString("comment"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getLong("user_id"),
                        (Long) rs.getObject("issued_policy_id"),
                        rs.getString("policy_number"),
                        rs.getString("policy_status"),
                        rs.getString("policy_type"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getBigDecimal("premium_amount"),
                        (Integer) rs.getObject("term_months"),
                        (Integer) rs.getObject("power_hp"),
                        (Boolean) rs.getObject("unlimited_drivers"),
                        rs.getString("vehicle_category_name"),
                        rs.getString("region_name"),
                        rs.getString("client_email"),
                        buildFio(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name")),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getString("reg_number"),
                        rs.getString("vin"),
                        rs.getObject("birth_date", LocalDate.class),
                        rs.getString("passport_series"),
                        rs.getString("passport_number"),
                        rs.getObject("passport_issue_date", LocalDate.class),
                        rs.getString("passport_issuer"),
                        rs.getString("registration_address"),
                        rs.getString("driver_license_number"),
                        rs.getObject("license_issued_date", LocalDate.class)
                ),
                id,
                agent.getId()
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }
        return rows.get(0);
    }

    @PostMapping("/{id}/take")
    public AgentActionResponse takeInWork(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id
    ) {
        UserEntity agent = requireAgent(principal);
        int updated = jdbcTemplate.update(
                """
                update insurance.policy_applications
                set status = 'IN_REVIEW',
                    updated_at = now()
                where id = ?
                  and assigned_agent_id = ?
                  and upper(status) in ('NEW', 'NEED_INFO')
                """,
                id,
                agent.getId()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application cannot be taken to review");
        }
        return new AgentActionResponse(id, "IN_REVIEW");
    }

    @PostMapping("/{id}/need-info")
    public AgentActionResponse needInfo(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestBody NeedInfoRequest req
    ) {
        UserEntity agent = requireAgent(principal);
        String comment = normalizeComment(req == null ? null : req.comment());
        if (comment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "comment is required");
        }
        int updated = jdbcTemplate.update(
                """
                update insurance.policy_applications
                set status = 'NEED_INFO',
                    comment = ?,
                    updated_at = now()
                where id = ?
                  and assigned_agent_id = ?
                  and upper(status) in ('NEW', 'IN_REVIEW', 'NEED_INFO')
                """,
                comment,
                id,
                agent.getId()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application cannot be moved to NEED_INFO");
        }
        notifyClientByApplication(id, "Нужны уточнения по заявке на полис", comment, null);
        return new AgentActionResponse(id, "NEED_INFO");
    }

    @PostMapping("/{id}/approve")
    public AgentActionResponse approve(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestBody ApproveRequest req
    ) {
        UserEntity agent = requireAgent(principal);
        String comment = normalizeComment(req == null ? null : req.comment());
        int updated = jdbcTemplate.update(
                """
                update insurance.policy_applications
                set status = 'APPROVED',
                    comment = ?,
                    updated_at = now()
                where id = ?
                  and assigned_agent_id = ?
                  and upper(status) in ('NEW', 'IN_REVIEW', 'NEED_INFO')
                """,
                comment,
                id,
                agent.getId()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application cannot be approved");
        }
        notifyClientByApplication(id, "Заявка на полис одобрена", "Можно переходить к оплате полиса.", "PAY_OSAGO:" + id);
        return new AgentActionResponse(id, "APPROVED");
    }

    @PostMapping("/{id}/reject")
    public AgentActionResponse reject(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestBody RejectRequest req
    ) {
        UserEntity agent = requireAgent(principal);
        String comment = normalizeComment(req == null ? null : req.comment());
        if (comment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "comment is required");
        }

        var appRows = jdbcTemplate.query(
                """
                select issued_policy_id
                from insurance.policy_applications
                where id = ?
                  and assigned_agent_id = ?
                limit 1
                """,
                (rs, rowNum) -> (Long) rs.getObject("issued_policy_id"),
                id,
                agent.getId()
        );
        if (appRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found");
        }

        int updated = jdbcTemplate.update(
                """
                update insurance.policy_applications
                set status = 'REJECTED',
                    comment = ?,
                    updated_at = now()
                where id = ?
                  and assigned_agent_id = ?
                  and upper(status) in ('NEW', 'IN_REVIEW', 'NEED_INFO', 'APPROVED')
                """,
                comment,
                id,
                agent.getId()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application cannot be rejected");
        }

        Long policyId = appRows.get(0);
        if (policyId != null) {
            jdbcTemplate.update(
                    """
                    update insurance.policies
                    set status = 'CANCELLED'::insurance.policy_status
                    where id = ?
                      and status in ('DRAFT'::insurance.policy_status, 'PENDING_PAY'::insurance.policy_status)
                    """,
                    policyId
            );
        }

        notifyClientByApplication(id, "Заявка на полис отклонена", comment, null);
        return new AgentActionResponse(id, "REJECTED");
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

    private void notifyClientByApplication(Long applicationId, String title, String message, String body) {
        var rows = jdbcTemplate.query(
                """
                select pa.user_id, p.number as policy_number
                from insurance.policy_applications pa
                left join insurance.policies p on p.id = pa.issued_policy_id
                where pa.id = ?
                limit 1
                """,
                (rs, rowNum) -> new NotifyTarget(
                        rs.getLong("user_id"),
                        rs.getString("policy_number")
                ),
                applicationId
        );
        if (rows.isEmpty()) {
            return;
        }
        NotifyTarget target = rows.get(0);
        String fullTitle = target.policyNumber() == null
                ? title
                : title + " (" + target.policyNumber() + ")";
        jdbcTemplate.update(
                """
                insert into insurance.notifications
                (recipient_id, type, title, message, body, is_read, created_at)
                values (?, ?, ?, ?, ?, false, now())
                """,
                target.userId(),
                "NEW_MESSAGE",
                fullTitle,
                message,
                body
        );
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase();
    }

    private String normalizeQuery(String value) {
        if (value == null) return null;
        String t = value.trim().toLowerCase();
        return t.isEmpty() ? null : t;
    }

    private String normalizeComment(String value) {
        if (value == null) return null;
        String t = value.trim();
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

    public record ApplicationsPageResponse(
            List<AgentApplicationShortDto> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record AgentApplicationShortDto(
            Long id,
            String status,
            String comment,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Long userId,
            Long policyId,
            String policyNumber,
            String policyStatus,
            BigDecimal premiumAmount,
            String clientEmail,
            String clientName,
            String brand,
            String model,
            String regNumber,
            String vin
    ) {
    }

    public record AgentApplicationDetailsDto(
            Long id,
            String status,
            String comment,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            Long userId,
            Long policyId,
            String policyNumber,
            String policyStatus,
            String policyType,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal premiumAmount,
            Integer termMonths,
            Integer powerHp,
            Boolean unlimitedDrivers,
            String vehicleCategoryName,
            String regionName,
            String clientEmail,
            String clientName,
            String brand,
            String model,
            String regNumber,
            String vin,
            LocalDate insuredBirthDate,
            String passportSeries,
            String passportNumber,
            LocalDate passportIssueDate,
            String passportIssuer,
            String registrationAddress,
            String driverLicenseNumber,
            LocalDate licenseIssuedDate
    ) {
    }

    public record NeedInfoRequest(String comment) {
    }

    public record ApproveRequest(String comment) {
    }

    public record RejectRequest(String comment) {
    }

    public record AgentActionResponse(Long id, String status) {
    }

    private record NotifyTarget(Long userId, String policyNumber) {
    }
}
