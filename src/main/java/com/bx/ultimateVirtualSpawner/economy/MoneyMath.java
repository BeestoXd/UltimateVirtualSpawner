package com.bx.ultimateVirtualSpawner.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyMath {

    public static final int MIN_DECIMAL_PLACES = 0;
    public static final int MAX_DECIMAL_PLACES = 6;

    private MoneyMath() {
    }

    public static double round(double value, int decimalPlaces) {
        if (!Double.isFinite(value)) {
            return 0D;
        }
        return BigDecimal.valueOf(value)
                .setScale(clampDecimalPlaces(decimalPlaces), RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static int clampDecimalPlaces(int decimalPlaces) {
        return Math.max(MIN_DECIMAL_PLACES, Math.min(MAX_DECIMAL_PLACES, decimalPlaces));
    }

    public static boolean equal(double left, double right, int decimalPlaces) {
        return Double.compare(round(left, decimalPlaces), round(right, decimalPlaces)) == 0;
    }
}
