package vkr.osago.policies;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PolicyWorkflow {
    private PolicyWorkflow() {
    }

    public static List<String> validateCreateRequest(PolicyApplicationData data) {
        List<String> errors = new ArrayList<>();
        if (data == null) {
            errors.add("request is required");
            return errors;
        }
        if (data.calcRequestId() == null || data.calcRequestId() <= 0) {
            errors.add("calcRequestId is required");
        }
        if (isBlank(data.vehicleBrand())) {
            errors.add("vehicleBrand is required");
        }
        if (isBlank(data.vinOrReg())) {
            errors.add("vinOrReg is required");
        }
        if (isBlank(data.insuredPersonName())) {
            errors.add("insuredPersonName is required");
        }
        if (data.startDate() == null) {
            errors.add("startDate is required");
        } else if (data.startDate().isBefore(LocalDate.now())) {
            errors.add("startDate cannot be in the past");
        }
        if (data.consentAccuracy() == null || !data.consentAccuracy()) {
            errors.add("consentAccuracy must be accepted");
        }
        if (data.consentPersonalData() == null || !data.consentPersonalData()) {
            errors.add("consentPersonalData must be accepted");
        }
        return errors;
    }

    public static PolicyApplicationStatus nextStatus(PolicyApplicationStatus current, PolicyAction action) {
        if (current == null || action == null) {
            throw new IllegalArgumentException("current status and action are required");
        }
        return switch (current) {
            case NEW -> switch (action) {
                case TAKE_IN_REVIEW -> PolicyApplicationStatus.IN_REVIEW;
                case REJECT -> PolicyApplicationStatus.REJECTED;
                default -> invalidTransition(current, action);
            };
            case IN_REVIEW -> switch (action) {
                case REQUEST_INFO -> PolicyApplicationStatus.NEED_INFO;
                case APPROVE -> PolicyApplicationStatus.APPROVED;
                case REJECT -> PolicyApplicationStatus.REJECTED;
                default -> invalidTransition(current, action);
            };
            case NEED_INFO -> switch (action) {
                case TAKE_IN_REVIEW -> PolicyApplicationStatus.IN_REVIEW;
                case REJECT -> PolicyApplicationStatus.REJECTED;
                default -> invalidTransition(current, action);
            };
            case APPROVED -> switch (action) {
                case PAY -> PolicyApplicationStatus.PAYMENT_PENDING;
                case REJECT -> PolicyApplicationStatus.REJECTED;
                default -> invalidTransition(current, action);
            };
            case PAYMENT_PENDING -> switch (action) {
                case CONFIRM_PAYMENT -> PolicyApplicationStatus.PAID;
                default -> invalidTransition(current, action);
            };
            case PAID, REJECTED -> invalidTransition(current, action);
        };
    }

    public static boolean canDeleteDraft(PolicyApplicationStatus appStatus, PolicyStatus policyStatus) {
        if (appStatus == null) return false;
        boolean allowedByStatus = appStatus == PolicyApplicationStatus.NEW
                || appStatus == PolicyApplicationStatus.IN_REVIEW
                || appStatus == PolicyApplicationStatus.NEED_INFO
                || appStatus == PolicyApplicationStatus.APPROVED
                || appStatus == PolicyApplicationStatus.PAYMENT_PENDING;
        boolean allowedByPolicy = policyStatus == null
                || policyStatus == PolicyStatus.DRAFT
                || policyStatus == PolicyStatus.PENDING_PAY;
        return allowedByStatus && allowedByPolicy;
    }

    private static PolicyApplicationStatus invalidTransition(PolicyApplicationStatus current, PolicyAction action) {
        throw new IllegalArgumentException("Transition " + current + " -> " + action + " is not allowed");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record PolicyApplicationData(
            Long calcRequestId,
            String vehicleBrand,
            String vinOrReg,
            String insuredPersonName,
            LocalDate startDate,
            Boolean consentAccuracy,
            Boolean consentPersonalData
    ) {
    }

    public enum PolicyApplicationStatus {
        NEW,
        IN_REVIEW,
        NEED_INFO,
        APPROVED,
        PAYMENT_PENDING,
        PAID,
        REJECTED
    }

    public enum PolicyStatus {
        DRAFT,
        PENDING_PAY,
        ACTIVE,
        CANCELLED
    }

    public enum PolicyAction {
        TAKE_IN_REVIEW,
        REQUEST_INFO,
        APPROVE,
        REJECT,
        PAY,
        CONFIRM_PAYMENT
    }
}
