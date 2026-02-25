package vkr.osago.osago;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public final class TariffCalculator {
    private TariffCalculator() {
    }

    public static CalcResult calculate(CalcInput input, TariffConfig config) {
        validate(input, config);

        BigDecimal categoryCoeff = config.categoryCoefficients().get(input.vehicleCategoryId());
        BigDecimal regionCoeff = config.regionCoefficients().get(input.regionId());
        BigDecimal powerCoeff = powerCoefficient(input.powerHp());
        BigDecimal termCoeff = termCoefficient(input.termMonths());
        BigDecimal driversCoeff = input.unlimitedDrivers()
                ? config.unlimitedDriversCoefficient()
                : config.limitedDriversCoefficient();

        BigDecimal amount = config.baseRate()
                .multiply(categoryCoeff)
                .multiply(regionCoeff)
                .multiply(powerCoeff)
                .multiply(termCoeff)
                .multiply(driversCoeff)
                .setScale(2, RoundingMode.HALF_UP);

        return new CalcResult(
                input.vehicleCategoryId(),
                input.regionId(),
                input.powerHp(),
                input.unlimitedDrivers(),
                input.termMonths(),
                amount
        );
    }

    public static SavedCalculation saveAndTransfer(
            CalcInput input,
            TariffConfig config,
            Long userId,
            CalculationStore store
    ) {
        if (store == null) throw new IllegalArgumentException("store is required");
        if (userId == null || userId <= 0) throw new IllegalArgumentException("userId is invalid");

        CalcResult result = calculate(input, config);
        Long calcId = store.save(userId, result);
        if (calcId == null || calcId <= 0) throw new IllegalStateException("Calculation id was not returned");

        DraftTransfer transfer = new DraftTransfer(
                calcId,
                userId,
                result.amount(),
                "READY_FOR_POLICY_APPLICATION"
        );
        return new SavedCalculation(calcId, result, transfer);
    }

    public static void validate(CalcInput input, TariffConfig config) {
        if (input == null) throw new IllegalArgumentException("input is required");
        if (config == null) throw new IllegalArgumentException("config is required");
        if (input.vehicleCategoryId() == null) throw new IllegalArgumentException("vehicleCategoryId is required");
        if (!config.categoryCoefficients().containsKey(input.vehicleCategoryId())) {
            throw new IllegalArgumentException("vehicleCategoryId is unsupported");
        }
        if (input.regionId() == null) throw new IllegalArgumentException("regionId is required");
        if (!config.regionCoefficients().containsKey(input.regionId())) {
            throw new IllegalArgumentException("regionId is unsupported");
        }
        if (input.powerHp() == null) throw new IllegalArgumentException("powerHp is required");
        if (input.powerHp() < 20 || input.powerHp() > 500) {
            throw new IllegalArgumentException("powerHp is out of range");
        }
        if (input.termMonths() == null) throw new IllegalArgumentException("termMonths is required");
        if (!(input.termMonths() == 3 || input.termMonths() == 6 || input.termMonths() == 12)) {
            throw new IllegalArgumentException("termMonths is unsupported");
        }
    }

    static BigDecimal powerCoefficient(int hp) {
        if (hp <= 70) return new BigDecimal("1.00");
        if (hp <= 100) return new BigDecimal("1.10");
        if (hp <= 150) return new BigDecimal("1.30");
        return new BigDecimal("1.50");
    }

    static BigDecimal termCoefficient(int months) {
        return switch (months) {
            case 3 -> new BigDecimal("0.50");
            case 6 -> new BigDecimal("0.70");
            case 12 -> new BigDecimal("1.00");
            default -> throw new IllegalArgumentException("termMonths is unsupported");
        };
    }

    public interface CalculationStore {
        Long save(Long userId, CalcResult result);
    }

    public record CalcInput(
            Long vehicleCategoryId,
            Long regionId,
            Integer powerHp,
            boolean unlimitedDrivers,
            Integer termMonths
    ) {
    }

    public record TariffConfig(
            BigDecimal baseRate,
            Map<Long, BigDecimal> categoryCoefficients,
            Map<Long, BigDecimal> regionCoefficients,
            BigDecimal limitedDriversCoefficient,
            BigDecimal unlimitedDriversCoefficient
    ) {
    }

    public record CalcResult(
            Long vehicleCategoryId,
            Long regionId,
            Integer powerHp,
            boolean unlimitedDrivers,
            Integer termMonths,
            BigDecimal amount
    ) {
    }

    public record DraftTransfer(
            Long calcId,
            Long userId,
            BigDecimal amount,
            String status
    ) {
    }

    public record SavedCalculation(
            Long calcId,
            CalcResult result,
            DraftTransfer transfer
    ) {
    }
}
