package vkr.osago.policies;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vkr.osago.user.UserRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/client/policies")
public class ClientPoliciesController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;

    public ClientPoliciesController(JdbcTemplate jdbcTemplate, UserRepository users) {
        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
    }

    @GetMapping
    public List<PolicyDto> myPolicies(@AuthenticationPrincipal UserDetails principal) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        purgeExpiredUnpaidApplications(user.getId());
        return jdbcTemplate.query(
                """
                select p.id, p.number, p.type::text as type, p.status::text as status,
                       p.start_date, p.end_date, p.premium_amount, p.created_at,
                       v.brand, v.model, v.reg_number
                from insurance.policies p
                left join insurance.vehicles v on v.id = p.vehicle_id
                where p.user_id = ?
                  and p.status <> 'CANCELLED'::insurance.policy_status
                order by p.created_at desc
                """,
                (rs, rowNum) -> new PolicyDto(
                        rs.getLong("id"),
                        rs.getString("number"),
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getBigDecimal("premium_amount"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getString("reg_number")
                ),
                user.getId()
        );
    }

    private void purgeExpiredUnpaidApplications(Long userId) {
        var stale = jdbcTemplate.query(
                """
                select pa.id, pa.issued_policy_id
                from insurance.policy_applications pa
                left join insurance.policies p on p.id = pa.issued_policy_id
                where pa.user_id = ?
                  and upper(pa.status) in ('APPROVED', 'PAYMENT_PENDING')
                  and pa.updated_at < (now() - interval '1 day')
                  and (
                        p.id is null
                        or p.status in ('DRAFT'::insurance.policy_status, 'PENDING_PAY'::insurance.policy_status)
                  )
                """,
                (rs, rowNum) -> new StaleAppRef(rs.getLong("id"), (Long) rs.getObject("issued_policy_id")),
                userId
        );

        for (var ref : stale) {
            jdbcTemplate.update(
                    "delete from insurance.policy_applications where id = ? and user_id = ?",
                    ref.applicationId(),
                    userId
            );
            if (ref.policyId() != null) {
                jdbcTemplate.update(
                        """
                        delete from insurance.policies
                        where id = ?
                          and user_id = ?
                          and status in ('DRAFT'::insurance.policy_status, 'PENDING_PAY'::insurance.policy_status)
                        """,
                        ref.policyId(),
                        userId
                );
            }
        }
    }

    @GetMapping("/{id}")
    public PolicyDetailDto policyById(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();

        var rows = jdbcTemplate.query(
                """
                select p.id, p.number, p.type::text as type, p.status::text as status,
                       p.start_date, p.end_date, p.premium_amount, p.created_at,
                       p.vehicle_category_id, c.name as vehicle_category_name,
                       p.region_id, r.name as region_name,
                       p.power_hp, p.unlimited_drivers, p.term_months,
                       v.brand, v.model, v.vin, v.reg_number
                from insurance.policies p
                left join insurance.vehicles v on v.id = p.vehicle_id
                left join insurance.ref_vehicle_categories c on c.id = p.vehicle_category_id
                left join insurance.ref_regions r on r.id = p.region_id
                where p.id = ? and p.user_id = ?
                limit 1
                """,
                (rs, rowNum) -> new PolicyDetailDto(
                        rs.getLong("id"),
                        rs.getString("number"),
                        rs.getString("type"),
                        rs.getString("status"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getBigDecimal("premium_amount"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getLong("vehicle_category_id"),
                        rs.getString("vehicle_category_name"),
                        rs.getLong("region_id"),
                        rs.getString("region_name"),
                        rs.getObject("power_hp", Integer.class),
                        rs.getObject("unlimited_drivers", Boolean.class),
                        rs.getObject("term_months", Integer.class),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getString("vin"),
                        rs.getString("reg_number")
                ),
                id,
                user.getId()
        );

        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found");
        }

        return rows.get(0);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> policyPdf(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        PolicyDetailDto policy = policyById(principal, id);

        List<String> lines = new ArrayList<>();
        lines.add("OSAGO POLICY");
        lines.add("Number: " + safe(policy.number(), "POL-" + policy.id()));
        lines.add("Status: " + safe(policy.status(), "-"));
        lines.add("Type: " + safe(policy.type(), "-"));
        lines.add("Vehicle: " + safe((policy.brand() == null ? "" : policy.brand()) + " " + (policy.model() == null ? "" : policy.model()), "-").trim());
        lines.add("Reg Number: " + safe(policy.regNumber(), "-"));
        lines.add("VIN: " + safe(policy.vin(), "-"));
        lines.add("Region: " + safe(policy.regionName(), "-"));
        lines.add("Category: " + safe(policy.vehicleCategoryName(), "-"));
        lines.add("Power HP: " + (policy.powerHp() == null ? "-" : policy.powerHp().toString()));
        lines.add("Term Months: " + (policy.termMonths() == null ? "-" : policy.termMonths().toString()));
        lines.add("Valid: " + safeDate(policy.startDate()) + " - " + safeDate(policy.endDate()));
        lines.add("Premium: " + (policy.premiumAmount() == null ? "-" : policy.premiumAmount().toPlainString()));
        lines.add("Created: " + (policy.createdAt() == null ? "-" : policy.createdAt().toString()));

        byte[] pdf = buildSimplePdf(lines);
        String filename = (policy.number() == null ? ("POL-" + policy.id()) : policy.number().replace(" ", "_")) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private String safe(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value;
    }

    private String safeDate(LocalDate date) {
        return date == null ? "-" : date.toString();
    }

    private byte[] buildSimplePdf(List<String> lines) {
        StringBuilder content = new StringBuilder();
        content.append("BT /F1 12 Tf 50 780 Td 14 TL ");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                content.append("T* ");
            }
            content.append("(").append(escapePdf(lines.get(i))).append(") Tj ");
        }
        content.append("ET");

        String stream = content.toString();
        byte[] streamBytes = stream.getBytes(StandardCharsets.US_ASCII);

        List<byte[]> objects = new ArrayList<>();
        objects.add("1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n".getBytes(StandardCharsets.US_ASCII));
        objects.add("2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n".getBytes(StandardCharsets.US_ASCII));
        objects.add("3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n".getBytes(StandardCharsets.US_ASCII));
        objects.add(("4 0 obj << /Length " + streamBytes.length + " >> stream\n" + stream + "\nendstream endobj\n").getBytes(StandardCharsets.US_ASCII));
        objects.add("5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n".getBytes(StandardCharsets.US_ASCII));

        String header = "%PDF-1.4\n";
        int offset = header.getBytes(StandardCharsets.US_ASCII).length;
        List<Integer> xref = new ArrayList<>();
        xref.add(0);

        for (byte[] obj : objects) {
            xref.add(offset);
            offset += obj.length;
        }

        StringBuilder xrefTable = new StringBuilder();
        xrefTable.append("xref\n");
        xrefTable.append("0 ").append(objects.size() + 1).append("\n");
        xrefTable.append("0000000000 65535 f \n");
        for (int i = 1; i < xref.size(); i++) {
            xrefTable.append(String.format("%010d 00000 n \n", xref.get(i)));
        }
        int startXref = offset;
        String trailer = "trailer << /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + startXref + "\n%%EOF";

        StringBuilder pdf = new StringBuilder(header);
        for (byte[] obj : objects) {
            pdf.append(new String(obj, StandardCharsets.US_ASCII));
        }
        pdf.append(xrefTable).append(trailer);
        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private String escapePdf(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)");
    }

    public record PolicyDto(
            Long id,
            String number,
            String type,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal premiumAmount,
            OffsetDateTime createdAt,
            String brand,
            String model,
            String regNumber
    ) {
    }

    public record PolicyDetailDto(
            Long id,
            String number,
            String type,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal premiumAmount,
            OffsetDateTime createdAt,
            Long vehicleCategoryId,
            String vehicleCategoryName,
            Long regionId,
            String regionName,
            Integer powerHp,
            Boolean unlimitedDrivers,
            Integer termMonths,
            String brand,
            String model,
            String vin,
            String regNumber
    ) {
    }

    private record StaleAppRef(Long applicationId, Long policyId) {
    }
}
