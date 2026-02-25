package vkr.osago.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vkr.osago.claims.repo.ClaimAttachmentRepository;
import vkr.osago.claims.entity.ClaimStatus;
import vkr.osago.user.UserEntity;
import vkr.osago.user.UserRepository;
import vkr.osago.user.UserStatus;

import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/agent/claims")
public class AgentClaimsController {

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository users;
    private final ClaimAttachmentRepository attachments;
    private final Path attachmentsRoot;

    public AgentClaimsController(
            JdbcTemplate jdbcTemplate,
            UserRepository users,
            ClaimAttachmentRepository attachments,
            @Value("${app.claims.attachments-root:uploads/claims}") String attachmentsRoot
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.users = users;
        this.attachments = attachments;
        this.attachmentsRoot = Paths.get(attachmentsRoot).toAbsolutePath().normalize();
    }

    @GetMapping
    public ClaimsPageResponse myClaims(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) Long claimId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UserEntity agent = users.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (agent.getStatus() != UserStatus.AGENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int offset = safePage * safeSize;

        StringBuilder where = new StringBuilder(" where c.assigned_agent_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(agent.getId());

        ClaimStatus statusFilter = parseStatus(status);
        if (statusFilter != null) {
            where.append(" and c.status = ?::insurance.claim_status ");
            args.add(statusFilter.name());
        }

        if (clientId != null) {
            where.append(" and c.user_id = ? ");
            args.add(clientId);
        }

        if (claimId != null) {
            where.append(" and c.id = ? ");
            args.add(claimId);
        }

        String normalizedQuery = normalizeQuery(q);
        if (normalizedQuery != null) {
            where.append("""
                     and (
                        lower(coalesce(c.number, '')) like ?
                        or lower(coalesce(p.number, '')) like ?
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
            String like = "%" + normalizedQuery + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }

        Long total = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.claims c
                left join insurance.policies p on p.id = c.policy_id
                join insurance.users u on u.id = c.user_id
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
                select c.id,
                       c.number,
                       c.status::text as status,
                       c.policy_id,
                       p.number as policy_number,
                       c.user_id,
                       u.last_name,
                       u.first_name,
                       u.middle_name,
                       c.description,
                       c.accident_at,
                       c.created_at,
                       c.updated_at
                from insurance.claims c
                left join insurance.policies p on p.id = c.policy_id
                join insurance.users u on u.id = c.user_id
                """ + where + """
                order by c.created_at desc, c.id desc
                limit ? offset ?
                """,
                (rs, rowNum) -> new AgentClaimShortDto(
                        rs.getLong("id"),
                        rs.getString("number"),
                        rs.getString("status"),
                        (Long) rs.getObject("policy_id"),
                        rs.getString("policy_number"),
                        rs.getLong("user_id"),
                        buildFio(
                                rs.getString("last_name"),
                                rs.getString("first_name"),
                                rs.getString("middle_name")
                        ),
                        sanitizeDescription(rs.getString("description")),
                        rs.getObject("accident_at", OffsetDateTime.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class)
                ),
                listArgs.toArray()
        );

        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil((double) totalElements / (double) safeSize);
        return new ClaimsPageResponse(items, safePage, safeSize, totalElements, totalPages);
    }

    @GetMapping("/{id}")
    public AgentClaimDetailsDto claimDetails(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id
    ) {
        UserEntity agent = requireAgent(principal);
        var rows = jdbcTemplate.query(
                """
                select c.id,
                       c.number,
                       c.status::text as status,
                       c.policy_id,
                       p.number as policy_number,
                       c.user_id,
                       u.last_name,
                       u.first_name,
                       u.middle_name,
                       u.email as client_email,
                       c.description,
                       c.accident_type::text as accident_type,
                       c.accident_at,
                       c.accident_place,
                       c.contact_phone,
                       c.contact_email,
                       c.approved_amount,
                       c.decision_comment,
                       c.decided_at,
                       c.paid_at,
                       c.created_at,
                       c.updated_at
                from insurance.claims c
                left join insurance.policies p on p.id = c.policy_id
                join insurance.users u on u.id = c.user_id
                where c.id = ?
                  and c.assigned_agent_id = ?
                limit 1
                """,
                (rs, rowNum) -> new AgentClaimDetailsDto(
                        rs.getLong("id"),
                        rs.getString("number"),
                        rs.getString("status"),
                        (Long) rs.getObject("policy_id"),
                        rs.getString("policy_number"),
                        rs.getLong("user_id"),
                        buildFio(rs.getString("last_name"), rs.getString("first_name"), rs.getString("middle_name")),
                        rs.getString("client_email"),
                        sanitizeDescription(rs.getString("description")),
                        rs.getString("accident_type"),
                        rs.getObject("accident_at", OffsetDateTime.class),
                        rs.getString("accident_place"),
                        rs.getString("contact_phone"),
                        rs.getString("contact_email"),
                        rs.getBigDecimal("approved_amount"),
                        rs.getString("decision_comment"),
                        rs.getObject("decided_at", OffsetDateTime.class),
                        rs.getObject("paid_at", OffsetDateTime.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("updated_at", OffsetDateTime.class),
                        List.of(),
                        List.of()
                ),
                id,
                agent.getId()
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
        }

        AgentClaimDetailsDto dto = rows.get(0);
        var attachmentDtos = attachments.findAllByClaimIdOrderByCreatedAtDesc(dto.id()).stream()
                .map(a -> new ClaimAttachmentDto(
                        a.getId(),
                        a.getFileName(),
                        a.getAttachmentType(),
                        a.getContentType(),
                        a.getCreatedAt()
                ))
                .toList();
        var history = loadHistoryOrFallback(dto.id(), dto.createdAt(), dto.updatedAt(), dto.status());

        return dto.withAttachmentsAndHistory(attachmentDtos, history);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @PathVariable Long attachmentId
    ) {
        UserEntity agent = requireAgent(principal);
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from insurance.claims
                where id = ?
                  and assigned_agent_id = ?
                """,
                Long.class,
                id,
                agent.getId()
        );
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
        }

        var attachment = attachments.findByIdAndClaimId(attachmentId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

        Resource resource = resolveAttachmentResource(attachment.getStorageKey());
        if (!resource.exists() || !resource.isReadable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file is unavailable");
        }

        String contentType = attachment.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(attachment.getFileName());
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(attachment.getFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(resource);
    }

    @PostMapping("/{id}/take")
    public AgentClaimShortActionDto takeInReview(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id
    ) {
        UserEntity agent = requireAgent(principal);
        var before = getClaimMinimalForAgent(id, agent.getId());
        int updated = jdbcTemplate.update(
                """
                update insurance.claims
                set status = 'IN_REVIEW'::insurance.claim_status,
                    updated_at = now()
                where id = ?
                  and assigned_agent_id = ?
                  and status in ('NEW'::insurance.claim_status, 'NEED_INFO'::insurance.claim_status)
                """,
                id,
                agent.getId()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot move claim to IN_REVIEW");
        }
        writeHistory(id, before.status(), "IN_REVIEW", "Заявка взята в работу", agent.getId());
        notifyClientAboutStatus(id, "IN_REVIEW", "Заявка принята в работу", "Агент начал проверку документов по заявке.");
        return new AgentClaimShortActionDto(id, "IN_REVIEW");
    }

    @PostMapping("/{id}/need-info")
    public AgentClaimShortActionDto requestAdditionalInfo(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestBody NeedInfoRequest req
    ) {
        UserEntity agent = requireAgent(principal);
        if (req == null || req.comment() == null || req.comment().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment is required");
        }

        var before = getClaimMinimalForAgent(id, agent.getId());
        if (!(before.status().equals("NEW") || before.status().equals("IN_REVIEW") || before.status().equals("NEED_INFO"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot request documents in status: " + before.status());
        }

        int updated = jdbcTemplate.update(
                """
                update insurance.claims
                set status = 'NEED_INFO'::insurance.claim_status,
                    decision_comment = ?,
                    updated_at = now()
                where id = ?
                  and assigned_agent_id = ?
                  and status in ('NEW'::insurance.claim_status, 'IN_REVIEW'::insurance.claim_status, 'NEED_INFO'::insurance.claim_status)
                """,
                req.comment().trim(),
                id,
                agent.getId()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot move claim to NEED_INFO");
        }

        writeHistory(id, before.status(), "NEED_INFO", req.comment().trim(), agent.getId());
        notifyClientAboutStatus(id, "NEED_INFO", "Нужны дополнительные документы", req.comment().trim());
        return new AgentClaimShortActionDto(id, "NEED_INFO");
    }

    @PostMapping("/{id}/approve")
    public AgentClaimShortActionDto approve(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestBody ApproveClaimRequest req
    ) {
        UserEntity agent = requireAgent(principal);
        if (req == null || req.approvedAmount() == null || req.approvedAmount().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "approvedAmount must be > 0");
        }

        var before = getClaimMinimalForAgent(id, agent.getId());
        if (!(before.status().equals("NEW") || before.status().equals("IN_REVIEW") || before.status().equals("NEED_INFO"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot approve claim in status: " + before.status());
        }
        String comment = trimToNull(req.comment());

        int updated = jdbcTemplate.update(
                """
                update insurance.claims
                set status = 'APPROVED'::insurance.claim_status,
                    approved_amount = ?,
                    decision_comment = ?,
                    decided_at = now(),
                    paid_at = null,
                    updated_at = now()
                where id = ?
                  and assigned_agent_id = ?
                  and status in ('NEW'::insurance.claim_status, 'IN_REVIEW'::insurance.claim_status, 'NEED_INFO'::insurance.claim_status)
                """,
                req.approvedAmount(),
                comment,
                id,
                agent.getId()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot approve claim");
        }

        writeHistory(
                id,
                before.status(),
                "APPROVED",
                "Одобрено. Сумма: " + req.approvedAmount() + (comment == null ? "" : ". " + comment),
                agent.getId()
        );
        notifyClientAboutStatus(
                id,
                "APPROVED",
                "Заявка одобрена",
                "Заявка одобрена. Сумма выплаты: " + req.approvedAmount() + " ₽."
        );
        return new AgentClaimShortActionDto(id, "APPROVED");
    }

    @PostMapping("/{id}/reject")
    public AgentClaimShortActionDto reject(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestBody RejectClaimRequest req
    ) {
        UserEntity agent = requireAgent(principal);
        if (req == null || req.comment() == null || req.comment().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comment is required");
        }

        var before = getClaimMinimalForAgent(id, agent.getId());
        if (!(before.status().equals("NEW") || before.status().equals("IN_REVIEW") || before.status().equals("NEED_INFO"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reject claim in status: " + before.status());
        }

        int updated = jdbcTemplate.update(
                """
                update insurance.claims
                set status = 'REJECTED'::insurance.claim_status,
                    approved_amount = null,
                    decision_comment = ?,
                    decided_at = now(),
                    paid_at = null,
                    updated_at = now()
                where id = ?
                  and assigned_agent_id = ?
                  and status in ('NEW'::insurance.claim_status, 'IN_REVIEW'::insurance.claim_status, 'NEED_INFO'::insurance.claim_status)
                """,
                req.comment().trim(),
                id,
                agent.getId()
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reject claim");
        }

        writeHistory(id, before.status(), "REJECTED", req.comment().trim(), agent.getId());
        notifyClientAboutStatus(id, "REJECTED", "Заявка отклонена", req.comment().trim());
        return new AgentClaimShortActionDto(id, "REJECTED");
    }

    private UserEntity requireAgent(UserDetails principal) {
        UserEntity agent = users.findByEmail(principal.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (agent.getStatus() != UserStatus.AGENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return agent;
    }

    private List<ClaimHistoryDto> loadHistoryOrFallback(Long claimId, OffsetDateTime createdAt, OffsetDateTime updatedAt, String status) {
        if (hasTable("insurance.claim_status_history")) {
            try {
                var rows = jdbcTemplate.query(
                        """
                        select created_at, old_status::text as old_status, new_status::text as new_status, comment
                        from insurance.claim_status_history
                        where claim_id = ?
                        order by created_at asc
                        """,
                        (rs, rowNum) -> new ClaimHistoryDto(
                                rs.getObject("created_at", OffsetDateTime.class),
                                rs.getString("old_status"),
                                rs.getString("new_status"),
                                rs.getString("comment")
                        ),
                        claimId
                );
                if (!rows.isEmpty()) {
                    return rows;
                }
            } catch (Exception ignored) {
            }
        }

        List<ClaimHistoryDto> fallback = new ArrayList<>();
        fallback.add(new ClaimHistoryDto(createdAt, null, "NEW", "Заявка создана"));
        if (updatedAt != null && createdAt != null && updatedAt.isAfter(createdAt.plusSeconds(1))) {
            fallback.add(new ClaimHistoryDto(updatedAt, null, status, "Последнее обновление статуса"));
        }
        return fallback;
    }

    private boolean hasTable(String qualifiedName) {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "select to_regclass(?) is not null",
                    Boolean.class,
                    qualifiedName
            );
            return Boolean.TRUE.equals(exists);
        } catch (Exception ex) {
            return false;
        }
    }

    private Resource resolveAttachmentResource(String storageKey) {
        try {
            if (storageKey == null || storageKey.isBlank()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Empty storage key");
            }

            if (storageKey.startsWith("http://") || storageKey.startsWith("https://")) {
                return new UrlResource(URI.create(storageKey));
            }

            Path path;
            if (storageKey.startsWith("file:")) {
                path = Paths.get(URI.create(storageKey)).toAbsolutePath().normalize();
            } else {
                path = attachmentsRoot.resolve(storageKey).toAbsolutePath().normalize();
            }

            if (!path.startsWith(attachmentsRoot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid storage key");
            }

            return new UrlResource(path.toUri());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid storage key", ex);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file is unavailable", ex);
        }
    }

    private ClaimMinimal getClaimMinimalForAgent(Long claimId, Long agentId) {
        var rows = jdbcTemplate.query(
                """
                select id, user_id, status::text as status
                from insurance.claims
                where id = ? and assigned_agent_id = ?
                limit 1
                """,
                (rs, rowNum) -> new ClaimMinimal(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("status")
                ),
                claimId,
                agentId
        );
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
        }
        return rows.get(0);
    }

    private void writeHistory(Long claimId, String oldStatus, String newStatus, String comment, Long changedByUserId) {
        if (!hasTable("insurance.claim_status_history")) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    insert into insurance.claim_status_history
                    (claim_id, old_status, new_status, comment, created_at, changed_by_user_id)
                    values (?, ?::insurance.claim_status, ?::insurance.claim_status, ?, now(), ?)
                    """,
                    claimId,
                    oldStatus,
                    newStatus,
                    trimToNull(comment),
                    changedByUserId
            );
        } catch (Exception ex) {
            // fallback for schemas without changed_by_user_id
            try {
                jdbcTemplate.update(
                        """
                        insert into insurance.claim_status_history
                        (claim_id, old_status, new_status, comment, created_at)
                        values (?, ?::insurance.claim_status, ?::insurance.claim_status, ?, now())
                        """,
                        claimId,
                        oldStatus,
                        newStatus,
                        trimToNull(comment)
                );
            } catch (Exception ignored) {
            }
        }
    }

    private void notifyClientAboutStatus(Long claimId, String status, String title, String message) {
        try {
            var row = jdbcTemplate.query(
                    """
                    select user_id, coalesce(number, ('#' || id::text)) as claim_number
                    from insurance.claims
                    where id = ?
                    limit 1
                    """,
                    (rs, rowNum) -> Map.of(
                            "userId", rs.getLong("user_id"),
                            "claimNumber", rs.getString("claim_number")
                    ),
                    claimId
            );
            if (row.isEmpty()) return;

            Long clientId = (Long) row.get(0).get("userId");
            String claimNumber = String.valueOf(row.get(0).get("claimNumber"));

            String finalTitle = "Обновление по страховому случаю " + claimNumber;
            String finalMessage = "По вашему страховому случаю " + claimNumber + " есть новая информация. " + message;

            jdbcTemplate.update(
                    """
                    insert into insurance.notifications
                    (recipient_id, type, title, message, is_read, created_at)
                    values (?, ?, ?, ?, false, now())
                    """,
                    clientId,
                    "CLAIM_" + status,
                    finalTitle,
                    finalMessage
            );
        } catch (Exception ignored) {
        }
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private ClaimStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ClaimStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
        }
    }

    private String normalizeQuery(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String sanitizeDescription(String description) {
        if (description == null) return null;
        String raw = description.trim();
        int idx = raw.indexOf("[Комментарий клиента");
        if (idx >= 0) {
            raw = raw.substring(0, idx).trim();
        }
        return raw;
    }

    private static String buildFio(String lastName, String firstName, String middleName) {
        StringBuilder sb = new StringBuilder();
        if (lastName != null && !lastName.isBlank()) {
            sb.append(lastName.trim());
        }
        if (firstName != null && !firstName.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(firstName.trim());
        }
        if (middleName != null && !middleName.isBlank()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(middleName.trim());
        }
        return sb.isEmpty() ? "Клиент" : sb.toString();
    }

    public record ClaimsPageResponse(
            List<AgentClaimShortDto> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record AgentClaimShortDto(
            Long id,
            String number,
            String status,
            Long policyId,
            String policyNumber,
            Long clientId,
            String clientName,
            String description,
            OffsetDateTime accidentAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AgentClaimDetailsDto(
            Long id,
            String number,
            String status,
            Long policyId,
            String policyNumber,
            Long clientId,
            String clientName,
            String clientEmail,
            String description,
            String accidentType,
            OffsetDateTime accidentAt,
            String accidentPlace,
            String contactPhone,
            String contactEmail,
            BigDecimal approvedAmount,
            String decisionComment,
            OffsetDateTime decidedAt,
            OffsetDateTime paidAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<ClaimAttachmentDto> attachments,
            List<ClaimHistoryDto> history
    ) {
        public AgentClaimDetailsDto withAttachmentsAndHistory(
                List<ClaimAttachmentDto> newAttachments,
                List<ClaimHistoryDto> newHistory
        ) {
            return new AgentClaimDetailsDto(
                    id, number, status, policyId, policyNumber, clientId, clientName, clientEmail, description,
                    accidentType, accidentAt, accidentPlace, contactPhone, contactEmail, approvedAmount,
                    decisionComment, decidedAt, paidAt, createdAt, updatedAt, newAttachments, newHistory
            );
        }
    }

    public record ClaimAttachmentDto(
            Long id,
            String fileName,
            String attachmentType,
            String contentType,
            OffsetDateTime createdAt
    ) {
    }

    public record ClaimHistoryDto(
            OffsetDateTime createdAt,
            String oldStatus,
            String newStatus,
            String comment
    ) {
    }

    public record AgentClaimShortActionDto(
            Long id,
            String status
    ) {
    }

    public record NeedInfoRequest(String comment) {
    }

    public record ApproveClaimRequest(BigDecimal approvedAmount, String comment) {
    }

    public record RejectClaimRequest(String comment) {
    }

    private record ClaimMinimal(
            Long id,
            Long userId,
            String status
    ) {
    }
}
