package vkr.osago.claims.dto;

import java.time.OffsetDateTime;

public record CloseRequest(
        boolean paid,
        OffsetDateTime paidAt
) { }
