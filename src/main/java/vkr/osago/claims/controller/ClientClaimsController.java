package vkr.osago.claims.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vkr.osago.agent.AgentAssignmentService;
import vkr.osago.claims.dto.CreateClientClaimRequest;
import vkr.osago.claims.entity.ClaimStatus;
import vkr.osago.claims.entity.ClaimEntity;
import vkr.osago.claims.repo.ClaimAttachmentRepository;
import vkr.osago.claims.repo.ClaimRepository;
import vkr.osago.user.UserRepository;

import java.math.BigDecimal;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/client/claims")
public class ClientClaimsController {

    private final ClaimRepository claims;
    private final ClaimAttachmentRepository attachments;
    private final UserRepository users;
    private final AgentAssignmentService agentAssignmentService;
    private final JdbcTemplate jdbcTemplate;
    private final Path attachmentsRoot;

    public ClientClaimsController(
            ClaimRepository claims,
            ClaimAttachmentRepository attachments,
            UserRepository users,
            AgentAssignmentService agentAssignmentService,
            JdbcTemplate jdbcTemplate,
            @Value("${app.claims.attachments-root:uploads/claims}") String attachmentsRoot
    ) {
        this.claims = claims;
        this.attachments = attachments;
        this.users = users;
        this.agentAssignmentService = agentAssignmentService;
        this.jdbcTemplate = jdbcTemplate;
        this.attachmentsRoot = Paths.get(attachmentsRoot).toAbsolutePath().normalize();
    }

    @GetMapping
    public Page<ClaimShortDto> myClaims(@AuthenticationPrincipal UserDetails principal, Pageable pageable) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();

        return claims.findAllByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(c -> new ClaimShortDto(
                        c.getId(),
                        c.getNumber(),
                        c.getPolicyId(),
                        sanitizeDescription(c.getDescription()),
                        c.getStatus().name(),
                        c.getAccidentAt(),
                        c.getCreatedAt(),
                        c.getUpdatedAt()
                ));
    }

    @GetMapping("/policies")
    public List<PolicyShortDto> myPolicies(@AuthenticationPrincipal UserDetails principal) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        return jdbcTemplate.query(
                """
                select id, number, status
                from insurance.policies
                where user_id = ?
                order by created_at desc
                """,
                (rs, rowNum) -> new PolicyShortDto(
                        rs.getLong("id"),
                        rs.getString("number"),
                        rs.getString("status")
                ),
                user.getId()
        );
    }

    @PostMapping
    public ClaimCreatedDto createClaim(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid CreateClientClaimRequest req
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        var now = OffsetDateTime.now(ZoneId.systemDefault());

        if (!Boolean.TRUE.equals(req.consentAccuracy()) || !Boolean.TRUE.equals(req.consentPersonalData())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо подтвердить оба согласия");
        }

        if (req.policyId() != null) {
            Integer exists = jdbcTemplate.queryForObject(
                    "select count(1) from insurance.policies where id = ? and user_id = ?",
                    Integer.class,
                    req.policyId(),
                    user.getId()
            );
            if (exists == null || exists == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Указанный полис не найден");
            }
        }

        Long assignedAgentId = agentAssignmentService.ensureAgentAssignedToUser(user.getId());

        var claim = new ClaimEntity();
        claim.setNumber(null);
        claim.setUserId(user.getId());
        claim.setPolicyId(req.policyId());
        claim.setStatus(ClaimStatus.NEW);
        claim.setDescription(req.description().trim());
        claim.setAssignedAgentId(assignedAgentId);
        claim.setCreatedAt(now);
        claim.setUpdatedAt(now);
        claim.setAccidentType(req.accidentType());
        claim.setAccidentAt(req.accidentAt());
        claim.setAccidentPlace(req.accidentPlace().trim());
        claim.setContactPhone(req.contactPhone().trim());
        claim.setContactEmail(req.contactEmail() == null ? null : req.contactEmail().trim());
        claim.setConsentPersonalData(req.consentPersonalData());
        claim.setConsentAccuracy(req.consentAccuracy());
        claim.setApprovedAmount(null);
        claim.setDecisionComment(null);
        claim.setDecidedAt(null);
        claim.setPaidAt(null);

        var saved = claims.save(claim);
        saved.setNumber(generateClaimNumber(saved.getId(), saved.getCreatedAt()));
        saved.setUpdatedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        saved = claims.save(saved);

        return new ClaimCreatedDto(
                saved.getId(),
                saved.getNumber(),
                saved.getStatus().name(),
                saved.getCreatedAt()
        );
    }

    @GetMapping("/{id}")
    public ClaimDetailsDto myClaimById(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        var claim = getOwnedClaim(principal, id);

        var attachmentDtos = attachments.findAllByClaimIdOrderByCreatedAtDesc(claim.getId()).stream()
                .map(a -> new AttachmentDto(
                        a.getId(),
                        a.getFileName(),
                        a.getContentType(),
                        a.getStorageKey(),
                        a.getCreatedAt(),
                        a.getAttachmentType()
                ))
                .toList();

        String policyNumber = null;
        if (claim.getPolicyId() != null) {
            try {
                policyNumber = jdbcTemplate.queryForObject(
                        "select number from insurance.policies where id = ?",
                        String.class,
                        claim.getPolicyId()
                );
            } catch (EmptyResultDataAccessException ignored) {
                policyNumber = null;
            }
        }

        String agentName = null;
        String agentEmail = null;
        String agentPhone = null;
        if (claim.getAssignedAgentId() != null) {
            var agent = users.findById(claim.getAssignedAgentId()).orElse(null);
            if (agent != null) {
                var middle = agent.getMiddleName() == null ? "" : (" " + agent.getMiddleName());
                agentName = agent.getLastName() + " " + agent.getFirstName() + middle;
                agentEmail = agent.getEmail();
            }
            try {
                agentPhone = jdbcTemplate.queryForObject(
                        "select phone from insurance.agent_profiles where user_id = ?",
                        String.class,
                        claim.getAssignedAgentId()
                );
            } catch (EmptyResultDataAccessException ignored) {
                agentPhone = null;
            }
        }

        return new ClaimDetailsDto(
                claim.getId(),
                claim.getNumber(),
                claim.getPolicyId(),
                policyNumber,
                claim.getStatus().name(),
                sanitizeDescription(claim.getDescription()),
                claim.getAccidentType() == null ? null : claim.getAccidentType().name(),
                claim.getAccidentAt(),
                claim.getAccidentPlace(),
                claim.getContactPhone(),
                claim.getContactEmail(),
                claim.getApprovedAmount(),
                claim.getDecisionComment(),
                claim.getDecidedAt(),
                claim.getPaidAt(),
                claim.getCreatedAt(),
                claim.getUpdatedAt(),
                claim.getAssignedAgentId(),
                agentName,
                agentEmail,
                agentPhone,
                attachmentDtos
        );
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @PathVariable Long attachmentId
    ) {
        var claim = getOwnedClaim(principal, id);
        var attachment = attachments.findByIdAndClaimId(attachmentId, claim.getId())
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

    @PostMapping("/{id}/note")
    public ClaimNoteResponse addClientNote(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestBody AddClaimNoteRequest req
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        var claim = getOwnedClaim(principal, id);

        String note = req == null || req.note() == null ? "" : req.note().trim();
        if (note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Комментарий обязателен");
        }
        if (note.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Комментарий слишком длинный");
        }
        if (claim.getStatus() == ClaimStatus.CLOSED || claim.getStatus() == ClaimStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "По этой заявке больше нельзя отправлять информацию");
        }

        String oldStatus = claim.getStatus().name();
        String newStatus = oldStatus;
        if (claim.getStatus() == ClaimStatus.NEED_INFO) {
            claim.setStatus(ClaimStatus.IN_REVIEW);
            newStatus = ClaimStatus.IN_REVIEW.name();
        }

        claim.setUpdatedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        claims.save(claim);

        writeHistoryIfPossible(claim.getId(), oldStatus, newStatus, "Комментарий клиента: " + note, user.getId());

        if (claim.getAssignedAgentId() != null) {
            String claimNumber = claim.getNumber() == null ? ("#" + claim.getId()) : claim.getNumber();
            createNotification(
                    claim.getAssignedAgentId(),
                    "CLAIM_CLIENT_NOTE",
                    "Клиент добавил информацию по заявке " + claimNumber,
                    "Страховой случай " + claimNumber + ": " + note
            );
        }

        return new ClaimNoteResponse(claim.getId(), claim.getNumber(), claim.getStatus().name(), claim.getUpdatedAt());
    }

    @PostMapping("/{id}/attachments")
    public AttachmentUploadResponse uploadAdditionalAttachment(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "attachmentType", required = false) String attachmentType
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        var claim = getOwnedClaim(principal, id);

        if (claim.getStatus() == ClaimStatus.CLOSED || claim.getStatus() == ClaimStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "По этой заявке нельзя загружать файлы");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл обязателен");
        }
        if (file.getSize() > 20L * 1024L * 1024L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Файл больше 20MB");
        }

        String safeFileName = sanitizeFileName(file.getOriginalFilename());
        String ext = "";
        int dot = safeFileName.lastIndexOf('.');
        if (dot > -1 && dot < safeFileName.length() - 1) {
            ext = safeFileName.substring(dot);
        }
        String storageKey = String.format("%d/%s%s", claim.getId(), UUID.randomUUID(), ext);
        Path target = attachmentsRoot.resolve(storageKey).normalize();
        if (!target.startsWith(attachmentsRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный путь файла");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить файл", ex);
        }

        String attType = normalizeAttachmentType(attachmentType, file.getContentType());
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneId.systemDefault());
        Long attachmentId = jdbcTemplate.queryForObject(
                """
                insert into insurance.claim_attachments
                (attachment_type, claim_id, content_type, created_at, file_name, storage_key)
                values (?::insurance.attachment_type, ?, ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                attType,
                claim.getId(),
                trimToNull(file.getContentType()),
                createdAt,
                safeFileName,
                storageKey
        );
        if (attachmentId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить метаданные файла");
        }

        String oldStatus = claim.getStatus().name();
        String newStatus = oldStatus;
        if (claim.getStatus() == ClaimStatus.NEED_INFO) {
            claim.setStatus(ClaimStatus.IN_REVIEW);
            newStatus = ClaimStatus.IN_REVIEW.name();
        }
        claim.setUpdatedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        claims.save(claim);

        writeHistoryIfPossible(
                claim.getId(),
                oldStatus,
                newStatus,
                "Клиент загрузил файл: " + safeFileName,
                user.getId()
        );

        if (claim.getAssignedAgentId() != null) {
            String claimNumber = claim.getNumber() == null ? ("#" + claim.getId()) : claim.getNumber();
            createNotification(
                    claim.getAssignedAgentId(),
                    "CLAIM_CLIENT_FILE",
                    "Новый файл по заявке " + claimNumber,
                    "Страховой случай " + claimNumber + ": клиент загрузил файл " + safeFileName
            );
        }

        return new AttachmentUploadResponse(
                attachmentId,
                safeFileName,
                attType,
                claim.getStatus().name(),
                createdAt
        );
    }

    @PostMapping("/{id}/payout-request")
    public PayoutRequestResponse requestPayout(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @RequestBody PayoutRequest req
    ) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        var claim = getOwnedClaim(principal, id);

        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Запрос выплаты доступен только для одобренной заявки");
        }
        String bankName = trimToNull(req == null ? null : req.bankName());
        String cardNumber = digitsOnly(req == null ? null : req.cardNumber());
        if (bankName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите банк");
        }
        if (cardNumber == null || cardNumber.length() < 16 || cardNumber.length() > 19) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный номер карты");
        }

        ensureClaimPayoutRequestsTable();
        String cardMasked = maskCard(cardNumber);

        Long payoutRequestId = jdbcTemplate.queryForObject(
                """
                insert into insurance.claim_payout_requests
                (claim_id, user_id, bank_name, card_masked, requested_at, status)
                values (?, ?, ?, ?, now(), 'REQUESTED')
                returning id
                """,
                Long.class,
                claim.getId(),
                user.getId(),
                bankName,
                cardMasked
        );
        if (payoutRequestId == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось создать запрос выплаты");
        }

        claim.setStatus(ClaimStatus.CLOSED);
        claim.setPaidAt(OffsetDateTime.now(ZoneId.systemDefault()));
        claim.setUpdatedAt(OffsetDateTime.now(ZoneId.systemDefault()));
        claims.save(claim);

        writeHistoryIfPossible(
                claim.getId(),
                ClaimStatus.APPROVED.name(),
                ClaimStatus.CLOSED.name(),
                "Клиент запросил выплату: " + bankName + ", карта " + cardMasked,
                user.getId()
        );

        String claimNumber = claim.getNumber() == null ? ("#" + claim.getId()) : claim.getNumber();
        createNotification(
                user.getId(),
                "CLAIM_PAYOUT_REQUESTED",
                "Запрос выплаты по случаю " + claimNumber,
                "По вашему страховому случаю " + claimNumber + " оформлен запрос выплаты на карту " + cardMasked + "."
        );
        if (claim.getAssignedAgentId() != null) {
            createNotification(
                    claim.getAssignedAgentId(),
                    "CLAIM_PAYOUT_REQUESTED",
                    "Клиент запросил выплату по заявке " + claimNumber,
                    "Страховой случай " + claimNumber + ": банк " + bankName + ", карта " + cardMasked + "."
            );
        }

        return new PayoutRequestResponse(
                payoutRequestId,
                claim.getId(),
                claim.getNumber(),
                claim.getStatus().name(),
                claim.getPaidAt()
        );
    }

    private ClaimEntity getOwnedClaim(UserDetails principal, Long claimId) {
        var user = users.findByEmail(principal.getUsername()).orElseThrow();
        return claims.findByIdAndUserId(claimId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
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

    private String generateClaimNumber(Long id, OffsetDateTime createdAt) {
        int year = createdAt != null ? createdAt.getYear() : OffsetDateTime.now().getYear();
        return String.format("CLM-%d-%06d", year, id);
    }

    private String sanitizeFileName(String original) {
        String name = original == null || original.isBlank() ? "attachment.bin" : original;
        name = name.replace("\\", "_").replace("/", "_").replace("..", "_");
        return name.length() > 180 ? name.substring(name.length() - 180) : name;
    }

    private String normalizeAttachmentType(String attachmentType, String contentType) {
        String type = trimToNull(attachmentType);
        if (type == null) {
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                return "DAMAGE_PHOTO";
            }
            return "ACCIDENT_DOC";
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if ("DAMAGE_PHOTO".equals(normalized) || "ACCIDENT_DOC".equals(normalized)) {
            return normalized;
        }
        return "ACCIDENT_DOC";
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private String digitsOnly(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("\\D+", "");
        return digits.isEmpty() ? null : digits;
    }

    private String maskCard(String cardNumber) {
        String digits = digitsOnly(cardNumber);
        if (digits == null || digits.length() < 4) {
            return "****";
        }
        String last4 = digits.substring(digits.length() - 4);
        return "**** **** **** " + last4;
    }

    private void ensureClaimPayoutRequestsTable() {
        jdbcTemplate.execute(
                """
                create table if not exists insurance.claim_payout_requests (
                    id bigserial primary key,
                    claim_id bigint not null references insurance.claims(id) on delete cascade,
                    user_id bigint not null references insurance.users(id) on delete restrict,
                    bank_name varchar(200) not null,
                    card_masked varchar(32) not null,
                    requested_at timestamptz not null default now(),
                    status varchar(20) not null default 'REQUESTED'
                )
                """
        );
    }

    private String sanitizeDescription(String description) {
        if (description == null) return null;
        String raw = description.trim();
        int idx = raw.indexOf("[Комментарий клиента");
        if (idx >= 0) {
            raw = raw.substring(0, idx).trim();
        }
        return raw;
    }

    private void createNotification(Long recipientId, String type, String title, String message) {
        jdbcTemplate.update(
                """
                insert into insurance.notifications
                (recipient_id, type, title, message, is_read, created_at)
                values (?, ?, ?, ?, false, now())
                """,
                recipientId,
                type,
                title,
                message
        );
    }

    private void writeHistoryIfPossible(Long claimId, String oldStatus, String newStatus, String comment, Long changedByUserId) {
        Boolean hasTable = jdbcTemplate.queryForObject("select to_regclass('insurance.claim_status_history') is not null", Boolean.class);
        if (!Boolean.TRUE.equals(hasTable)) {
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
                    oldStatus.toUpperCase(Locale.ROOT),
                    newStatus.toUpperCase(Locale.ROOT),
                    comment,
                    changedByUserId
            );
        } catch (Exception ex) {
            try {
                jdbcTemplate.update(
                        """
                        insert into insurance.claim_status_history
                        (claim_id, old_status, new_status, comment, created_at)
                        values (?, ?::insurance.claim_status, ?::insurance.claim_status, ?, now())
                        """,
                        claimId,
                        oldStatus.toUpperCase(Locale.ROOT),
                        newStatus.toUpperCase(Locale.ROOT),
                        comment
                );
            } catch (Exception ignored) {
            }
        }
    }

    public record ClaimShortDto(
            Long id,
            String number,
            Long policyId,
            String description,
            String status,
            OffsetDateTime accidentAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record PolicyShortDto(
            Long id,
            String number,
            String status
    ) {
    }

    public record ClaimCreatedDto(
            Long id,
            String number,
            String status,
            OffsetDateTime createdAt
    ) {
    }

    public record ClaimDetailsDto(
            Long id,
            String number,
            Long policyId,
            String policyNumber,
            String status,
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
            Long assignedAgentId,
            String agentName,
            String agentEmail,
            String agentPhone,
            List<AttachmentDto> attachments
    ) {
    }

    public record AttachmentDto(
            Long id,
            String fileName,
            String contentType,
            String storageKey,
            OffsetDateTime createdAt,
            String attachmentType
    ) {
    }

    public record AddClaimNoteRequest(String note) {
    }

    public record ClaimNoteResponse(
            Long claimId,
            String claimNumber,
            String status,
            OffsetDateTime updatedAt
    ) {
    }

    public record AttachmentUploadResponse(
            Long attachmentId,
            String fileName,
            String attachmentType,
            String claimStatus,
            OffsetDateTime createdAt
    ) {
    }

    public record PayoutRequest(
            String bankName,
            String cardNumber
    ) {
    }

    public record PayoutRequestResponse(
            Long payoutRequestId,
            Long claimId,
            String claimNumber,
            String claimStatus,
            OffsetDateTime paidAt
    ) {
    }
}
