package vkr.osago.claims.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vkr.osago.claims.entity.AccidentType;

import java.time.OffsetDateTime;

public record CreateClientClaimRequest(
        Long policyId,
        @NotNull AccidentType accidentType,
        @NotNull OffsetDateTime accidentAt,
        @NotBlank String accidentPlace,
        @NotBlank String description,
        @NotBlank String contactPhone,
        @Email String contactEmail,
        @NotNull Boolean consentPersonalData,
        @NotNull Boolean consentAccuracy
) {
}
