package vkr.osago.osago;

import java.time.OffsetDateTime;

public final class PaymentWorkflow {
    private PaymentWorkflow() {
    }

    public static PaymentStart startPayment(PaymentRequest request, AppStatus appStatus) {
        if (request == null) throw new IllegalArgumentException("request is required");
        if (appStatus == null) throw new IllegalArgumentException("application status is required");
        if (!(appStatus == AppStatus.APPROVED || appStatus == AppStatus.PAYMENT_PENDING)) {
            throw new IllegalStateException("Application is not approved by agent");
        }
        validateCard(request.cardNumber(), request.expMonth(), request.expYear(), request.cvv());
        return new PaymentStart(PaymentStatus.NEW, AppStatus.PAYMENT_PENDING, PolicyStatus.PENDING_PAY);
    }

    public static PaymentConfirm confirmSuccess(PaymentStatus currentPaymentStatus, AppStatus currentAppStatus) {
        if (currentPaymentStatus == null || currentAppStatus == null) {
            throw new IllegalArgumentException("statuses are required");
        }
        if (!(currentAppStatus == AppStatus.PAYMENT_PENDING || currentAppStatus == AppStatus.APPROVED)) {
            throw new IllegalStateException("Payment is not in pending state");
        }
        if (!(currentPaymentStatus == PaymentStatus.NEW || currentPaymentStatus == PaymentStatus.PENDING)) {
            throw new IllegalStateException("Payment is not created");
        }
        return new PaymentConfirm(
                PaymentStatus.SUCCESS,
                AppStatus.PAID,
                PolicyStatus.ACTIVE,
                true,
                "Оплата полиса успешна",
                "Полис активирован. Статус: ACTIVE.",
                OffsetDateTime.now()
        );
    }

    public static PaymentFailure failPayment(PaymentStatus currentPaymentStatus, AppStatus appStatus) {
        if (currentPaymentStatus == null || appStatus == null) {
            throw new IllegalArgumentException("statuses are required");
        }
        if (!(currentPaymentStatus == PaymentStatus.NEW || currentPaymentStatus == PaymentStatus.PENDING)) {
            throw new IllegalStateException("Payment cannot be failed in current status");
        }
        return new PaymentFailure(
                PaymentStatus.FAILED,
                appStatus == AppStatus.PAYMENT_PENDING ? AppStatus.APPROVED : appStatus,
                PolicyStatus.DRAFT,
                false
        );
    }

    public static void validateCard(String cardNumber, Integer expMonth, Integer expYear, String cvv) {
        String digits = onlyDigits(cardNumber);
        if (digits.length() < 13 || digits.length() > 19) {
            throw new IllegalArgumentException("Card number length is invalid");
        }
        if (!luhnValid(digits)) {
            throw new IllegalArgumentException("Card number failed Luhn check");
        }
        if (expMonth == null || expMonth < 1 || expMonth > 12) {
            throw new IllegalArgumentException("Expiration month is invalid");
        }
        if (expYear == null || expYear < 2024 || expYear > 2100) {
            throw new IllegalArgumentException("Expiration year is invalid");
        }
        String cvvDigits = onlyDigits(cvv);
        if (cvvDigits.length() < 3 || cvvDigits.length() > 4) {
            throw new IllegalArgumentException("CVV is invalid");
        }
    }

    static boolean luhnValid(String number) {
        int sum = 0;
        boolean alternate = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = number.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }

    private static String onlyDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D+", "");
    }

    public enum PaymentStatus {
        NEW,
        PENDING,
        SUCCESS,
        FAILED
    }

    public enum AppStatus {
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
        ACTIVE
    }

    public record PaymentRequest(
            String cardNumber,
            Integer expMonth,
            Integer expYear,
            String cvv
    ) {
    }

    public record PaymentStart(
            PaymentStatus paymentStatus,
            AppStatus applicationStatus,
            PolicyStatus policyStatus
    ) {
    }

    public record PaymentConfirm(
            PaymentStatus paymentStatus,
            AppStatus applicationStatus,
            PolicyStatus policyStatus,
            boolean notifyClient,
            String notificationTitle,
            String notificationMessage,
            OffsetDateTime paidAt
    ) {
    }

    public record PaymentFailure(
            PaymentStatus paymentStatus,
            AppStatus applicationStatus,
            PolicyStatus policyStatus,
            boolean notifyClient
    ) {
    }
}
