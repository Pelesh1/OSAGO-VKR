package vkr.osago.claims;

import vkr.osago.claims.entity.AccidentType;
import vkr.osago.claims.entity.ClaimStatus;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class ClaimWorkflow {
    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;

    private ClaimWorkflow() {
    }

    public static List<String> validateCreate(ClaimCreateData data) {
        List<String> errors = new ArrayList<>();
        if (data == null) {
            errors.add("request is required");
            return errors;
        }
        if (isBlank(data.description()) || data.description().trim().length() < 10) {
            errors.add("description is too short");
        }
        if (data.accidentType() == null) {
            errors.add("accidentType is required");
        }
        if (data.accidentAt() == null) {
            errors.add("accidentAt is required");
        }
        if (isBlank(data.accidentPlace())) {
            errors.add("accidentPlace is required");
        }
        if (isBlank(data.contactPhone()) || digitsOnly(data.contactPhone()).length() < 10) {
            errors.add("contactPhone is invalid");
        }
        if (data.consentAccuracy() == null || !data.consentAccuracy()) {
            errors.add("consentAccuracy must be accepted");
        }
        if (data.consentPersonalData() == null || !data.consentPersonalData()) {
            errors.add("consentPersonalData must be accepted");
        }
        return errors;
    }

    public static List<String> validateAttachment(ClaimStatus status, String fileName, long sizeBytes) {
        List<String> errors = new ArrayList<>();
        if (status == ClaimStatus.CLOSED || status == ClaimStatus.REJECTED) {
            errors.add("attachments are not allowed for closed or rejected claims");
        }
        if (isBlank(fileName)) {
            errors.add("fileName is required");
        }
        if (sizeBytes <= 0) {
            errors.add("file is empty");
        }
        if (sizeBytes > MAX_FILE_SIZE) {
            errors.add("file is too large");
        }
        return errors;
    }

    public static ClaimStatus nextStatus(ClaimStatus current, ClaimAction action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("current status and action are required");
        }
        return switch (current) {
            case NEW -> switch (action) {
                case TAKE_IN_REVIEW -> ClaimStatus.IN_REVIEW;
                case REJECT -> ClaimStatus.REJECTED;
                default -> invalidTransition(current, action);
            };
            case IN_REVIEW -> switch (action) {
                case REQUEST_INFO -> ClaimStatus.NEED_INFO;
                case APPROVE -> ClaimStatus.APPROVED;
                case REJECT -> ClaimStatus.REJECTED;
                default -> invalidTransition(current, action);
            };
            case NEED_INFO -> switch (action) {
                case CLIENT_UPDATE -> ClaimStatus.IN_REVIEW;
                case REJECT -> ClaimStatus.REJECTED;
                default -> invalidTransition(current, action);
            };
            case APPROVED, REJECTED -> switch (action) {
                case CLOSE -> ClaimStatus.CLOSED;
                default -> invalidTransition(current, action);
            };
            case CLOSED -> invalidTransition(current, action);
        };
    }

    public static Decision validateDecision(ClaimAction action, Decision data) {
        if (action == ClaimAction.APPROVE) {
            if (data == null || data.approvedAmount() == null || data.approvedAmount().signum() <= 0) {
                throw new IllegalArgumentException("approvedAmount must be > 0");
            }
            return data;
        }
        if (action == ClaimAction.REJECT) {
            if (data == null || isBlank(data.decisionComment())) {
                throw new IllegalArgumentException("decisionComment is required");
            }
            return data;
        }
        return data;
    }

    public static boolean canOperate(ClaimStatus status) {
        return status != ClaimStatus.CLOSED && status != ClaimStatus.REJECTED;
    }

    public static HistoryEntry history(String oldStatus, String newStatus, String comment, Long userId) {
        return new HistoryEntry(oldStatus, newStatus, comment, OffsetDateTime.now(), userId);
    }

    private static ClaimStatus invalidTransition(ClaimStatus current, ClaimAction action) {
        throw new IllegalArgumentException("Transition " + current + " -> " + action + " is not allowed");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D+", "");
    }

    public record ClaimCreateData(
            Long policyId,
            String description,
            AccidentType accidentType,
            OffsetDateTime accidentAt,
            String accidentPlace,
            String contactPhone,
            String contactEmail,
            Boolean consentAccuracy,
            Boolean consentPersonalData
    ) {
    }

    public record Decision(
            java.math.BigDecimal approvedAmount,
            String decisionComment
    ) {
    }

    public record HistoryEntry(
            String oldStatus,
            String newStatus,
            String comment,
            OffsetDateTime createdAt,
            Long changedByUserId
    ) {
    }

    public enum ClaimAction {
        TAKE_IN_REVIEW,
        REQUEST_INFO,
        CLIENT_UPDATE,
        APPROVE,
        REJECT,
        CLOSE
    }
}
