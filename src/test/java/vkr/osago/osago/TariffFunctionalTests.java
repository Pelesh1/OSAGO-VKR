package vkr.osago.osago;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TariffFunctionalTests {

    private TariffCalculator.TariffConfig testConfig() {
        return new TariffCalculator.TariffConfig(
                new BigDecimal("5000.00"),
                Map.of(
                        1L, new BigDecimal("1.00"),
                        2L, new BigDecimal("1.25"),
                        3L, new BigDecimal("0.90")
                ),
                Map.of(
                        10L, new BigDecimal("1.00"),
                        20L, new BigDecimal("1.40"),
                        30L, new BigDecimal("0.95")
                ),
                new BigDecimal("1.00"),
                new BigDecimal("1.80")
        );
    }

    @Test
    void shouldCalculateForValidParameters() {
        var input = new TariffCalculator.CalcInput(1L, 10L, 120, false, 12);
        var result = TariffCalculator.calculate(input, testConfig());
        assertEquals(new BigDecimal("6500.00"), result.amount());
    }

    @Test
    void shouldDifferByCategoryAndRegion() {
        var base = new TariffCalculator.CalcInput(1L, 10L, 100, false, 12);
        var byCategory = new TariffCalculator.CalcInput(2L, 10L, 100, false, 12);
        var byRegion = new TariffCalculator.CalcInput(1L, 20L, 100, false, 12);

        var resultBase = TariffCalculator.calculate(base, testConfig());
        var resultCategory = TariffCalculator.calculate(byCategory, testConfig());
        var resultRegion = TariffCalculator.calculate(byRegion, testConfig());

        assertNotEquals(resultBase.amount(), resultCategory.amount());
        assertNotEquals(resultBase.amount(), resultRegion.amount());
    }

    @Test
    void shouldDifferByDriversMode() {
        var limited = new TariffCalculator.CalcInput(1L, 10L, 100, false, 12);
        var unlimited = new TariffCalculator.CalcInput(1L, 10L, 100, true, 12);

        var limitedResult = TariffCalculator.calculate(limited, testConfig());
        var unlimitedResult = TariffCalculator.calculate(unlimited, testConfig());

        assertTrue(unlimitedResult.amount().compareTo(limitedResult.amount()) > 0);
    }

    @Test
    void shouldHandlePowerAndTermBoundaries() {
        var minPower = new TariffCalculator.CalcInput(1L, 10L, 20, false, 3);
        var maxPower = new TariffCalculator.CalcInput(1L, 10L, 500, false, 12);

        var minResult = TariffCalculator.calculate(minPower, testConfig());
        var maxResult = TariffCalculator.calculate(maxPower, testConfig());

        assertNotNull(minResult.amount());
        assertNotNull(maxResult.amount());
        assertTrue(maxResult.amount().compareTo(minResult.amount()) > 0);
    }

    @Test
    void shouldRejectInvalidParameters() {
        var config = testConfig();
        assertThrows(IllegalArgumentException.class,
                () -> TariffCalculator.calculate(new TariffCalculator.CalcInput(null, 10L, 100, false, 12), config));
        assertThrows(IllegalArgumentException.class,
                () -> TariffCalculator.calculate(new TariffCalculator.CalcInput(1L, 999L, 100, false, 12), config));
        assertThrows(IllegalArgumentException.class,
                () -> TariffCalculator.calculate(new TariffCalculator.CalcInput(1L, 10L, 10, false, 12), config));
        assertThrows(IllegalArgumentException.class,
                () -> TariffCalculator.calculate(new TariffCalculator.CalcInput(1L, 10L, 100, false, 9), config));
    }

    @Test
    void shouldSaveCalculationAndTransferToPolicyFlow() {
        var input = new TariffCalculator.CalcInput(1L, 10L, 120, false, 12);
        AtomicReference<TariffCalculator.CalcResult> savedResult = new AtomicReference<>();

        TariffCalculator.CalculationStore store = (userId, result) -> {
            assertEquals(55L, userId);
            savedResult.set(result);
            return 101L;
        };

        var saved = TariffCalculator.saveAndTransfer(input, testConfig(), 55L, store);

        assertEquals(101L, saved.calcId());
        assertNotNull(savedResult.get());
        assertEquals(saved.result().amount(), saved.transfer().amount());
        assertEquals("READY_FOR_POLICY_APPLICATION", saved.transfer().status());
    }

    @Test
    void shouldCoverValidationAndStoreErrorBranches() {
        var config = testConfig();
        var input = new TariffCalculator.CalcInput(1L, 10L, 120, false, 12);

        assertThrows(IllegalArgumentException.class, () -> TariffCalculator.validate(null, config));
        assertThrows(IllegalArgumentException.class, () -> TariffCalculator.validate(input, null));
        assertThrows(IllegalArgumentException.class, () ->
                TariffCalculator.validate(new TariffCalculator.CalcInput(999L, 10L, 120, false, 12), config));
        assertThrows(IllegalArgumentException.class, () ->
                TariffCalculator.validate(new TariffCalculator.CalcInput(1L, null, 120, false, 12), config));
        assertThrows(IllegalArgumentException.class, () ->
                TariffCalculator.validate(new TariffCalculator.CalcInput(1L, 10L, null, false, 12), config));
        assertThrows(IllegalArgumentException.class, () ->
                TariffCalculator.validate(new TariffCalculator.CalcInput(1L, 10L, 501, false, 12), config));
        assertThrows(IllegalArgumentException.class, () ->
                TariffCalculator.validate(new TariffCalculator.CalcInput(1L, 10L, 120, false, null), config));

        assertThrows(IllegalArgumentException.class, () -> TariffCalculator.saveAndTransfer(input, config, null, (u, r) -> 1L));
        assertThrows(IllegalArgumentException.class, () -> TariffCalculator.saveAndTransfer(input, config, -1L, (u, r) -> 1L));
        assertThrows(IllegalArgumentException.class, () -> TariffCalculator.saveAndTransfer(input, config, 1L, null));
        assertThrows(IllegalStateException.class, () -> TariffCalculator.saveAndTransfer(input, config, 1L, (u, r) -> null));
        assertThrows(IllegalStateException.class, () -> TariffCalculator.saveAndTransfer(input, config, 1L, (u, r) -> 0L));
    }

    @Test
    void shouldCoverPowerAndTermBranches() {
        assertEquals(new BigDecimal("1.00"), TariffCalculator.powerCoefficient(50));
        assertEquals(new BigDecimal("1.10"), TariffCalculator.powerCoefficient(90));
        assertEquals(new BigDecimal("1.30"), TariffCalculator.powerCoefficient(130));
        assertEquals(new BigDecimal("1.50"), TariffCalculator.powerCoefficient(200));

        assertEquals(new BigDecimal("0.50"), TariffCalculator.termCoefficient(3));
        assertEquals(new BigDecimal("0.70"), TariffCalculator.termCoefficient(6));
        assertEquals(new BigDecimal("1.00"), TariffCalculator.termCoefficient(12));
        assertThrows(IllegalArgumentException.class, () -> TariffCalculator.termCoefficient(2));
    }
}
