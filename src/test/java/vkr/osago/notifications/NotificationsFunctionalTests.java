package vkr.osago.notifications;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationsFunctionalTests {

    @Test
    void shouldCreateNotificationsOnKeyEvents() {
        var payment = NotificationWorkflow.createOnEvent(
                1L, 10L, NotificationWorkflow.KeyEvent.POLICY_PAYMENT_SUCCESS, 100L, OffsetDateTime.now()
        );
        assertEquals("PAYMENT", payment.type());
        assertTrue(payment.title().contains("Оплата полиса"));
        assertFalse(payment.isRead());

        var claim = NotificationWorkflow.createOnEvent(
                2L, 10L, NotificationWorkflow.KeyEvent.CLAIM_NEED_INFO, 55L, OffsetDateTime.now()
        );
        assertEquals("CLAIM", claim.type());
    }

    @Test
    void shouldReturnListSortedByTime() {
        var t1 = OffsetDateTime.now().minusMinutes(10);
        var t2 = OffsetDateTime.now();
        var list = List.of(
                new NotificationWorkflow.Notification(1L, 10L, "A", "a", "m", "CLAIM:1", false, t1, null),
                new NotificationWorkflow.Notification(2L, 10L, "B", "b", "m", "CLAIM:2", false, t2, null),
                new NotificationWorkflow.Notification(3L, 11L, "C", "c", "m", "CLAIM:3", false, t2, null)
        );

        var result = NotificationWorkflow.listForUserSorted(list, 10L, 20);
        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).id());
        assertEquals(1L, result.get(1).id());
    }

    @Test
    void shouldCountUnread() {
        var list = List.of(
                new NotificationWorkflow.Notification(1L, 10L, "A", "a", "m", "CLAIM:1", false, OffsetDateTime.now(), null),
                new NotificationWorkflow.Notification(2L, 10L, "A", "a", "m", "CLAIM:1", true, OffsetDateTime.now(), OffsetDateTime.now()),
                new NotificationWorkflow.Notification(3L, 11L, "A", "a", "m", "CLAIM:1", false, OffsetDateTime.now(), null)
        );
        assertEquals(1, NotificationWorkflow.unreadCount(list, 10L));
    }

    @Test
    void shouldMarkAsRead() {
        var n = new NotificationWorkflow.Notification(1L, 10L, "A", "a", "m", "CLAIM:1", false, OffsetDateTime.now(), null);
        var updated = NotificationWorkflow.markRead(n, 10L);

        assertTrue(updated.isRead());
        assertNotNull(updated.readAt());
    }

    @Test
    void shouldResolveTargetRoute() {
        var claim = new NotificationWorkflow.Notification(1L, 10L, "A", "a", "m", "CLAIM:77", false, OffsetDateTime.now(), null);
        assertTrue(NotificationWorkflow.targetRoute(claim).contains("claims/detail.html?id=77"));

        var pay = new NotificationWorkflow.Notification(2L, 10L, "A", "a", "m", "PAY_OSAGO:123", false, OffsetDateTime.now(), null);
        assertTrue(NotificationWorkflow.targetRoute(pay).contains("payAppId=123"));

        var direct = new NotificationWorkflow.Notification(3L, 10L, "A", "a", "m", "/cabinet/client/chat/index.html", false, OffsetDateTime.now(), null);
        assertEquals("/cabinet/client/chat/index.html", NotificationWorkflow.targetRoute(direct));
    }

    @Test
    void shouldHandleEmptyNotificationsList() {
        assertTrue(NotificationWorkflow.listForUserSorted(null, 10L, 20).isEmpty());
        assertEquals(0, NotificationWorkflow.unreadCount(null, 10L));
        assertTrue(NotificationWorkflow.emptyIfNull(null).isEmpty());
    }

    @Test
    void shouldCoverValidationAndFallbackBranches() {
        assertThrows(IllegalArgumentException.class, () ->
                NotificationWorkflow.createOnEvent(1L, null, NotificationWorkflow.KeyEvent.CHAT_MESSAGE, 1L, OffsetDateTime.now()));
        assertThrows(IllegalArgumentException.class, () ->
                NotificationWorkflow.createOnEvent(1L, 1L, null, 1L, OffsetDateTime.now()));

        var notMine = new NotificationWorkflow.Notification(1L, 11L, "A", "a", "m", "x", false, OffsetDateTime.now(), null);
        assertThrows(IllegalArgumentException.class, () -> NotificationWorkflow.markRead(notMine, 10L));
        assertThrows(IllegalArgumentException.class, () -> NotificationWorkflow.markRead(null, 10L));

        var alreadyRead = new NotificationWorkflow.Notification(1L, 10L, "A", "a", "m", "x", true, OffsetDateTime.now(), OffsetDateTime.now());
        assertSame(alreadyRead, NotificationWorkflow.markRead(alreadyRead, 10L));

        assertEquals("/cabinet/client/index.html", NotificationWorkflow.targetRoute(null));
        assertEquals("/cabinet/client/index.html", NotificationWorkflow.targetRoute(
                new NotificationWorkflow.Notification(1L, 10L, "A", "a", "m", "", false, OffsetDateTime.now(), null)));
        assertEquals("/cabinet/client/index.html", NotificationWorkflow.targetRoute(
                new NotificationWorkflow.Notification(1L, 10L, "A", "a", "m", "UNKNOWN", false, OffsetDateTime.now(), null)));

        assertTrue(NotificationWorkflow.listForUserSorted(List.of(), -1L, 1).isEmpty());
        assertTrue(NotificationWorkflow.listForUserSorted(List.of(), 1L, 1000).isEmpty());
    }
}
