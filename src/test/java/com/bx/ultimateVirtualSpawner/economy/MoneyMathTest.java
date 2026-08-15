package com.bx.ultimateVirtualSpawner.economy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoneyMathTest {

    @ParameterizedTest
    @CsvSource({
            "0.1,          2, 0.10",
            "2.345,        2, 2.35",
            "2.344,        2, 2.34",
            "2.5,          0, 3",
            "-2.345,       2, -2.35",
            "1234.5678,    2, 1234.57",
            "1234.5678,    4, 1234.5678",
            "0.005,        2, 0.01"
    })
    @DisplayName("rounds half-up to the requested precision")
    void roundsHalfUp(double input, int places, double expected) {
        assertEquals(expected, MoneyMath.round(input, places), 1.0E-9);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    @DisplayName("non-finite input collapses to zero instead of poisoning a balance")
    void nonFiniteCollapsesToZero(double input) {
        assertEquals(0D, MoneyMath.round(input, 2));
    }

    @Test
    @DisplayName("repeated addition does not drift into fractions of a cent")
    void repeatedAdditionDoesNotDrift() {
        double balance = 0D;
        for (int i = 0; i < 10_000; i++) {
            balance = MoneyMath.round(balance + 0.1D, 2);
        }
        assertEquals(1000.00D, balance, 1.0E-9);

        double unrounded = 0D;
        for (int i = 0; i < 10_000; i++) {
            unrounded += 0.1D;
        }
        assertFalse(unrounded == 1000.00D, "expected raw double addition to drift");
    }

    @Test
    @DisplayName("the classic 0.1 + 0.2 case stays exact after rounding")
    void classicFloatingPointCaseIsClean() {
        assertEquals(0.30D, MoneyMath.round(0.1D + 0.2D, 2), 1.0E-9);
        assertTrue(MoneyMath.equal(0.1D + 0.2D, 0.3D, 2));
    }

    @Test
    @DisplayName("sell-then-spend round trips return to the starting balance")
    void depositWithdrawRoundTrip() {
        double balance = 0D;
        for (int i = 0; i < 500; i++) {
            balance = MoneyMath.round(balance + 12.34D, 2);
        }
        for (int i = 0; i < 500; i++) {
            balance = MoneyMath.round(balance - 12.34D, 2);
        }
        assertEquals(0D, balance, 1.0E-9);
    }

    @ParameterizedTest
    @CsvSource({"-5, 0", "0, 0", "2, 2", "6, 6", "7, 6", "999, 6"})
    @DisplayName("decimal places are clamped into a sane range")
    void clampsDecimalPlaces(int input, int expected) {
        assertEquals(expected, MoneyMath.clampDecimalPlaces(input));
    }

    @Test
    @DisplayName("an out-of-range precision does not blow up round()")
    void roundToleratesOutOfRangePrecision() {
        assertEquals(2.35D, MoneyMath.round(2.345D, 2), 1.0E-9);
        assertEquals(2D, MoneyMath.round(2.345D, -3), 1.0E-9);
        assertEquals(2.345D, MoneyMath.round(2.345D, 99), 1.0E-9);
    }

    @Test
    @DisplayName("equal() compares money, not raw doubles")
    void equalComparesRoundedValues() {
        assertTrue(MoneyMath.equal(10.001D, 10.004D, 2));
        assertFalse(MoneyMath.equal(10.001D, 10.006D, 2));
        assertTrue(MoneyMath.equal(10.001D, 10.006D, 1));
    }
}
