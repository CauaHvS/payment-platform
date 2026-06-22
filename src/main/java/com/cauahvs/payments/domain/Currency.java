package com.cauahvs.payments.domain;

public enum Currency {
    BRL(2),
    USD(2),
    EUR(2);

    private final int minorUnitScale;

    Currency(int minorUnitScale) {
        this.minorUnitScale = minorUnitScale;
    }

    public int minorUnitScale() {
        return minorUnitScale;
    }
}
