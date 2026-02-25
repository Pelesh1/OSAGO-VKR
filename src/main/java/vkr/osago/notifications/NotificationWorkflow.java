package vkr.osago.notifications;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class NotificationWorkflow {
    private NotificationWorkflow() {
    }

    public static Notification createOnEvent(
            Long id,
            Long recipientId,
            KeyEvent event,
            Long objectId,
            OffsetDateTime createdAt
    ) {
        if (recipientId == null || recipientId <= 0) {
            throw new IllegalArgumentException("recipientId is invalid");
        }
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }

        return switch (event) {
            case POLICY_PAYMENT_SUCCESS -> new Notification(
                    id,
                    recipientId,
                    "PAYMENT",
                    "Оплата полиса успешна",
                    "Полис активирован. Статус: ACTIVE.",
                    "POLICY:" + nullSafeId(objectId),
                    false,
                    createdAt,
                    null
            );
            case CLAIM_NEED_INFO -> new Notification(
                    id,
                    recipientId,
                    "CLAIM",
                    "Нужны документы по страховому случаю",
                    "По страховому случаю требуется дополнительная информация.",
                    "CLAIM:" + nullSafeId(objectId),
                    false,
                    createdAt,
                    null
            );
            case CLAIM_APPROVED -> new Notification(
                    id,
                    recipientId,
                    "CLAIM",
                    "Решение по страховому случаю",
                    "Страховой случай одобрен.",
                    "CLAIM:" + nullSafeId(objectId),
                    false,
                    createdAt,
                    null
            );
            case CHAT_MESSAGE -> new Notification(
                    id,
                    recipientId,
                    "CHAT",
                    "Новое сообщение в чате",
                    "У вас новое сообщение от агента.",
                    "/cabinet/client/chat/index.html",
                    false,
                    createdAt,
                    null
            );
        };
    }

    public static List<Notification> listForUserSorted(List<Notification> source, Long recipientId, int limit) {
        if (source == null) return List.of();
        if (recipientId == null || recipientId <= 0) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return source.stream()
                .filter(n -> recipientId.equals(n.recipientId()))
                .sorted(Comparator.comparing(Notification::createdAt).reversed())
                .limit(safeLimit)
                .toList();
    }

    public static long unreadCount(List<Notification> source, Long recipientId) {
        if (source == null || recipientId == null) return 0;
        return source.stream()
                .filter(n -> recipientId.equals(n.recipientId()))
                .filter(n -> !Boolean.TRUE.equals(n.isRead()))
                .count();
    }

    public static Notification markRead(Notification notification, Long recipientId) {
        if (notification == null) throw new IllegalArgumentException("notification is required");
        if (recipientId == null || !recipientId.equals(notification.recipientId())) {
            throw new IllegalArgumentException("notification does not belong to recipient");
        }
        if (Boolean.TRUE.equals(notification.isRead())) {
            return notification;
        }
        return new Notification(
                notification.id(),
                notification.recipientId(),
                notification.type(),
                notification.title(),
                notification.message(),
                notification.body(),
                true,
                notification.createdAt(),
                OffsetDateTime.now()
        );
    }

    public static String targetRoute(Notification notification) {
        if (notification == null) return "/cabinet/client/index.html";
        String body = notification.body();
        if (body == null || body.isBlank()) return "/cabinet/client/index.html";
        String normalized = body.trim();
        if (normalized.startsWith("/")) return normalized;
        if (normalized.toUpperCase(Locale.ROOT).startsWith("PAY_OSAGO:")) {
            return "/cabinet/client/policies/index.html?payAppId=" + normalized.substring("PAY_OSAGO:".length());
        }
        if (normalized.toUpperCase(Locale.ROOT).startsWith("CLAIM:")) {
            return "/cabinet/client/claims/detail.html?id=" + normalized.substring("CLAIM:".length());
        }
        if (normalized.toUpperCase(Locale.ROOT).startsWith("POLICY:")) {
            return "/cabinet/client/policies/detail.html?id=" + normalized.substring("POLICY:".length());
        }
        return "/cabinet/client/index.html";
    }

    public static List<Notification> emptyIfNull(List<Notification> source) {
        return source == null ? new ArrayList<>() : source;
    }

    private static String nullSafeId(Long id) {
        return id == null ? "" : id.toString();
    }

    public enum KeyEvent {
        POLICY_PAYMENT_SUCCESS,
        CLAIM_NEED_INFO,
        CLAIM_APPROVED,
        CHAT_MESSAGE
    }

    public record Notification(
            Long id,
            Long recipientId,
            String type,
            String title,
            String message,
            String body,
            Boolean isRead,
            OffsetDateTime createdAt,
            OffsetDateTime readAt
    ) {
    }
}
