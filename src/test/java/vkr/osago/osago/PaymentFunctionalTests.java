package vkr.osago.osago;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentFunctionalTests {

    @Test
    void shouldPaySuccessfullyInAllowedStatus() {
        var request = new PaymentWorkflow.PaymentRequest("4111 1111 1111 1111", 12, 2030, "123");
        var start = PaymentWorkflow.startPayment(request, PaymentWorkflow.AppStatus.APPROVED);

        assertEquals(PaymentWorkflow.PaymentStatus.NEW, start.paymentStatus());
        assertEquals(PaymentWorkflow.AppStatus.PAYMENT_PENDING, start.applicationStatus());
        assertEquals(PaymentWorkflow.PolicyStatus.PENDING_PAY, start.policyStatus());
    }

    @Test
    void shouldBlockPaymentInInvalidStatus() {
        var request = new PaymentWorkflow.PaymentRequest("4111111111111111", 12, 2030, "123");
        assertThrows(IllegalStateException.class,
                () -> PaymentWorkflow.startPayment(request, PaymentWorkflow.AppStatus.NEW));
        assertThrows(IllegalStateException.class,
                () -> PaymentWorkflow.startPayment(request, PaymentWorkflow.AppStatus.REJECTED));
    }

    @Test
    void shouldValidateCardFields() {
        assertDoesNotThrow(() ->
                PaymentWorkflow.validateCard("4111111111111111", 10, 2030, "123"));

        assertThrows(IllegalArgumentException.class,
                () -> PaymentWorkflow.validateCard("123", 10, 2030, "123"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentWorkflow.validateCard("4111111111111112", 10, 2030, "123"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentWorkflow.validateCard("4111111111111111", 0, 2030, "123"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentWorkflow.validateCard("4111111111111111", 10, 2000, "123"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentWorkflow.validateCard("4111111111111111", 10, 2030, "1"));
    }

    @Test
    void shouldUpdateStatusesOnConfirm() {
        var confirm = PaymentWorkflow.confirmSuccess(
                PaymentWorkflow.PaymentStatus.NEW,
                PaymentWorkflow.AppStatus.PAYMENT_PENDING
        );

        assertEquals(PaymentWorkflow.PaymentStatus.SUCCESS, confirm.paymentStatus());
        assertEquals(PaymentWorkflow.AppStatus.PAID, confirm.applicationStatus());
        assertEquals(PaymentWorkflow.PolicyStatus.ACTIVE, confirm.policyStatus());
        assertTrue(confirm.notifyClient());
        assertNotNull(confirm.paidAt());
    }

    @Test
    void shouldHandlePaymentFailureWithoutPolicyActivation() {
        var failure = PaymentWorkflow.failPayment(
                PaymentWorkflow.PaymentStatus.PENDING,
                PaymentWorkflow.AppStatus.PAYMENT_PENDING
        );

        assertEquals(PaymentWorkflow.PaymentStatus.FAILED, failure.paymentStatus());
        assertEquals(PaymentWorkflow.AppStatus.APPROVED, failure.applicationStatus());
        assertEquals(PaymentWorkflow.PolicyStatus.DRAFT, failure.policyStatus());
        assertFalse(failure.notifyClient());
    }

    @Test
    void shouldCreateClientNotificationOnSuccess() {
        var confirm = PaymentWorkflow.confirmSuccess(
                PaymentWorkflow.PaymentStatus.PENDING,
                PaymentWorkflow.AppStatus.APPROVED
        );

        assertTrue(confirm.notifyClient());
        assertTrue(confirm.notificationTitle().contains("Оплата полиса успешна"));
        assertTrue(confirm.notificationMessage().contains("ACTIVE"));
    }

    @Test
    void shouldCoverRemainingBranches() {
        assertFalse(PaymentWorkflow.luhnValid("4111111111111112"));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentWorkflow.startPayment(null, PaymentWorkflow.AppStatus.APPROVED));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentWorkflow.startPayment(new PaymentWorkflow.PaymentRequest("4111111111111111", 12, 2030, "123"), null));

        assertThrows(IllegalArgumentException.class,
                () -> PaymentWorkflow.confirmSuccess(null, PaymentWorkflow.AppStatus.APPROVED));
        assertThrows(IllegalStateException.class,
                () -> PaymentWorkflow.confirmSuccess(PaymentWorkflow.PaymentStatus.SUCCESS, PaymentWorkflow.AppStatus.PAYMENT_PENDING));
        assertThrows(IllegalStateException.class,
                () -> PaymentWorkflow.confirmSuccess(PaymentWorkflow.PaymentStatus.NEW, PaymentWorkflow.AppStatus.NEW));

        assertThrows(IllegalArgumentException.class,
                () -> PaymentWorkflow.failPayment(null, PaymentWorkflow.AppStatus.APPROVED));
        assertThrows(IllegalStateException.class,
                () -> PaymentWorkflow.failPayment(PaymentWorkflow.PaymentStatus.SUCCESS, PaymentWorkflow.AppStatus.APPROVED));
    }
}
