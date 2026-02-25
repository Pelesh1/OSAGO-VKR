package vkr.osago.agent;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/agent/reports")
public class AgentReportsController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;

    public AgentReportsController(JdbcTemplate jdbcTemplate, UserRepository users) {
        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
    }

    @GetMapping
    public AgentReportResponse report(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "POLICIES") String type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        UserEntity agent = requireAgent(principal);
        ReportType reportType = ReportType.parse(type);
        DateRange range = normalizeRange(from, to);

        return switch (reportType) {
            case POLICIES -> buildPoliciesReport(agent.getId(), range);
            case CLAIMS -> buildClaimsReport(agent.getId(), range);
        };
    }

    @GetMapping(value = "/export.csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "POLICIES") String type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to
    ) {
        AgentReportResponse data = report(principal, type, from, to);

        StringBuilder sb = new StringBuilder();
        sb.append("sep=;\n");
        sb.append("report_type;").append(escapeCsv(data.type())).append("\n");
        sb.append("from;").append(data.from()).append("\n");
        sb.append("to;").append(data.to()).append("\n\n");

        for (ReportMetricDto metric : data.metrics()) {
            sb.append(escapeCsv(metric.label())).append(';').append(escapeCsv(metric.value())).append("\n");
        }

        sb.append("\n");
        if (!data.rows().isEmpty()) {
            List<String> headers = data.rows().get(0).cells().stream().map(ReportCellDto::label).toList();
            sb.append(String.join(";", headers.stream().map(this::escapeCsv).toList())).append("\n");
            for (ReportRowDto row : data.rows()) {
                sb.append(String.join(";", row.cells().stream().map(c -> escapeCsv(c.value())).toList())).append("\n");
            }
        }

        String fileName = "agent_report_" + data.type().toLowerCase(Locale.ROOT) + "_" + OffsetDateTime.now().toLocalDate() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private AgentReportResponse buildPoliciesReport(Long agentId, DateRange range) {
        List<ReportRowDto> rows = jdbcTemplate.query(
                """
                select pa.id,
                       pa.status,
                       pa.created_at,
                       p.number as policy_number,
                       p.type::text as policy_type,
                       p.premium_amount,
                       u.last_name,
                       u.first_name,
                       u.middle_name,
                       v.brand,
                       v.model,
                       v.reg_number
                from insurance.policy_applications pa
                left join insurance.policies p on p.id = pa.issued_policy_id
                join insurance.users u on u.id = pa.user_id
                left join insurance.vehicles v on v.id = pa.vehicle_id
                where pa.assigned_agent_id = ?
                  and pa.created_at >= ?::date
                  and pa.created_at < (?::date + interval '1 day')
                order by pa.created_at desc, pa.id desc
                """,
                (rs, rowNum) -> new ReportRowDto(
                        List.of(
                                new ReportCellDto("app_id", String.valueOf(rs.getLong("id"))),
                                new ReportCellDto("created_at", formatDateTime(rs.getObject("created_at", OffsetDateTime.class))),
                                new ReportCellDto("client", buildFio(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name"))),
                                new ReportCellDto("policy_number", safe(rs.getString("policy_number"), "-")),
                                new ReportCellDto("policy_type", normalizeUpper(rs.getString("policy_type"))),
                                new ReportCellDto("vehicle", safe(joinVehicle(rs.getString("brand"), rs.getString("model")), "-")),
                                new ReportCellDto("reg_number", safe(rs.getString("reg_number"), "-")),
                                new ReportCellDto("status", normalizeUpper(rs.getString("status"))),
                                new ReportCellDto("premium_amount", number(rs.getBigDecimal("premium_amount")))
                        )
                ),
                agentId,
                range.from(),
                range.to()
        );

        Long total = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.policy_applications pa
                where pa.assigned_agent_id = ?
                  and pa.created_at >= ?::date
                  and pa.created_at < (?::date + interval '1 day')
                """,
                Long.class,
                agentId,
                range.from(),
                range.to()
        );

        Long paid = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.policy_applications pa
                where pa.assigned_agent_id = ?
                  and upper(pa.status) = 'PAID'
                  and pa.created_at >= ?::date
                  and pa.created_at < (?::date + interval '1 day')
                """,
                Long.class,
                agentId,
                range.from(),
                range.to()
        );

        Long pending = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.policy_applications pa
                where pa.assigned_agent_id = ?
                  and upper(pa.status) in ('NEW','IN_REVIEW','NEED_INFO','APPROVED','PAYMENT_PENDING')
                  and pa.created_at >= ?::date
                  and pa.created_at < (?::date + interval '1 day')
                """,
                Long.class,
                agentId,
                range.from(),
                range.to()
        );

        BigDecimal paidAmount = jdbcTemplate.queryForObject(
                """
                select coalesce(sum(p.premium_amount), 0)
                from insurance.policy_applications pa
                join insurance.policies p on p.id = pa.issued_policy_id
                where pa.assigned_agent_id = ?
                  and upper(pa.status) = 'PAID'
                  and pa.created_at >= ?::date
                  and pa.created_at < (?::date + interval '1 day')
                """,
                BigDecimal.class,
                agentId,
                range.from(),
                range.to()
        );

        return new AgentReportResponse(
                "POLICIES",
                range.from(),
                range.to(),
                List.of(
                        new ReportMetricDto("total_apps", String.valueOf(nullToZero(total))),
                        new ReportMetricDto("paid_apps", String.valueOf(nullToZero(paid))),
                        new ReportMetricDto("in_progress", String.valueOf(nullToZero(pending))),
                        new ReportMetricDto("paid_amount", number(paidAmount))
                ),
                rows
        );
    }

    private AgentReportResponse buildClaimsReport(Long agentId, DateRange range) {
        List<ReportRowDto> rows = jdbcTemplate.query(
                """
                select c.id,
                       c.number,
                       c.status::text as status,
                       c.created_at,
                       c.accident_at,
                       c.approved_amount,
                       p.number as policy_number,
                       u.last_name,
                       u.first_name,
                       u.middle_name
                from insurance.claims c
                left join insurance.policies p on p.id = c.policy_id
                join insurance.users u on u.id = c.user_id
                where c.assigned_agent_id = ?
                  and c.created_at >= ?::date
                  and c.created_at < (?::date + interval '1 day')
                order by c.created_at desc, c.id desc
                """,
                (rs, rowNum) -> new ReportRowDto(
                        List.of(
                                new ReportCellDto("claim_number", safe(rs.getString("number"), "CLM-" + rs.getLong("id"))),
                                new ReportCellDto("created_at", formatDateTime(rs.getObject("created_at", OffsetDateTime.class))),
                                new ReportCellDto("accident_at", formatDateTime(rs.getObject("accident_at", OffsetDateTime.class))),
                                new ReportCellDto("client", buildFio(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name"))),
                                new ReportCellDto("policy_number", safe(rs.getString("policy_number"), "-")),
                                new ReportCellDto("status", normalizeUpper(rs.getString("status"))),
                                new ReportCellDto("approved_amount", number(rs.getBigDecimal("approved_amount")))
                        )
                ),
                agentId,
                range.from(),
                range.to()
        );

        Long total = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.claims c
                where c.assigned_agent_id = ?
                  and c.created_at >= ?::date
                  and c.created_at < (?::date + interval '1 day')
                """,
                Long.class,
                agentId,
                range.from(),
                range.to()
        );

        Long approved = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.claims c
                where c.assigned_agent_id = ?
                  and coalesce(c.approved_amount, 0) > 0
                  and c.created_at >= ?::date
                  and c.created_at < (?::date + interval '1 day')
                """,
                Long.class,
                agentId,
                range.from(),
                range.to()
        );

        Long rejected = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.claims c
                where c.assigned_agent_id = ?
                  and c.status = 'REJECTED'::insurance.claim_status
                  and c.created_at >= ?::date
                  and c.created_at < (?::date + interval '1 day')
                """,
                Long.class,
                agentId,
                range.from(),
                range.to()
        );

        BigDecimal approvedAmount = jdbcTemplate.queryForObject(
                """
                select coalesce(sum(c.approved_amount), 0)
                from insurance.claims c
                where c.assigned_agent_id = ?
                  and coalesce(c.approved_amount, 0) > 0
                  and c.created_at >= ?::date
                  and c.created_at < (?::date + interval '1 day')
                """,
                BigDecimal.class,
                agentId,
                range.from(),
                range.to()
        );

        return new AgentReportResponse(
                "CLAIMS",
                range.from(),
                range.to(),
                List.of(
                        new ReportMetricDto("total_claims", String.valueOf(nullToZero(total))),
                        new ReportMetricDto("approved_claims", String.valueOf(nullToZero(approved))),
                        new ReportMetricDto("rejected_claims", String.valueOf(nullToZero(rejected))),
                        new ReportMetricDto("approved_amount", number(approvedAmount))
                ),
                rows
        );
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

    private DateRange normalizeRange(LocalDate from, LocalDate to) {
        LocalDate now = LocalDate.now();
        LocalDate safeTo = to == null ? now : to;
        LocalDate safeFrom = from == null ? safeTo.minusDays(29) : from;
        if (safeFrom.isAfter(safeTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be <= to");
        }
        return new DateRange(safeFrom, safeTo);
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
        return sb.isEmpty() ? "Client" : sb.toString();
    }

    private String joinVehicle(String brand, String model) {
        String b = brand == null ? "" : brand.trim();
        String m = model == null ? "" : model.trim();
        String value = (b + " " + m).trim();
        return value.isEmpty() ? null : value;
    }

    private String formatDateTime(OffsetDateTime value) {
        return value == null ? "" : value.toLocalDateTime().toString().replace('T', ' ');
    }

    private String number(BigDecimal value) {
        if (value == null) return "0";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private long nullToZero(Long value) {
        return value == null ? 0 : value;
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String normalizeUpper(String value) {
        if (value == null) return "";
        return value.toUpperCase(Locale.ROOT);
    }

    private String escapeCsv(String value) {
        String v = value == null ? "" : value;
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public record AgentReportResponse(
            String type,
            LocalDate from,
            LocalDate to,
            List<ReportMetricDto> metrics,
            List<ReportRowDto> rows
    ) {
    }

    public record ReportMetricDto(String label, String value) {
    }

    public record ReportRowDto(List<ReportCellDto> cells) {
    }

    public record ReportCellDto(String label, String value) {
    }

    private enum ReportType {
        POLICIES,
        CLAIMS;

        static ReportType parse(String value) {
            if (value == null || value.isBlank()) return POLICIES;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown report type: " + value);
            }
        }
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
