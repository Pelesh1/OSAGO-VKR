package vkr.osago.claims.dto;

import java.math.BigDecimal;

public record ApproveRequest(
        BigDecimal approvedAmount,
        String decisionComment
) { }
