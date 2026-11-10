package io.github.ydeliorman.ledgerkit;

import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money held as an exact count of minor units (cents, kuruş, ...).
 * No floating point is involved anywhere, and all arithmetic is overflow checked.
 */
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long minorUnits, String currencyCode) {
        return new Money(minorUnits, Currency.getInstance(currencyCode));
    }

    public static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    public boolean isZero() {
        return minorUnits == 0;
    }

    public boolean isPositive() {
        return minorUnits > 0;
    }

    public boolean isNegative() {
        return minorUnits < 0;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new LedgerException.CurrencyMismatch(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        int digits = Math.max(currency.getDefaultFractionDigits(), 0);
        if (digits == 0) {
            return minorUnits + " " + currency.getCurrencyCode();
        }
        long abs = Math.abs(minorUnits);
        long divisor = 1;
        for (int i = 0; i < digits; i++) {
            divisor *= 10;
        }
        String sign = minorUnits < 0 ? "-" : "";
        String fraction = String.format("%0" + digits + "d", abs % divisor);
        return sign + (abs / divisor) + "." + fraction + " " + currency.getCurrencyCode();
    }
}
