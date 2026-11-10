package io.github.ydeliorman.ledgerkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void addsAndSubtractsExactly() {
        Money a = Money.of(1050, "USD");
        Money b = Money.of(295, "USD");
        assertEquals(Money.of(1345, "USD"), a.plus(b));
        assertEquals(Money.of(755, "USD"), a.minus(b));
        assertEquals(Money.of(-1050, "USD"), a.negate());
    }

    @Test
    void refusesToMixCurrencies() {
        assertThrows(LedgerException.CurrencyMismatch.class,
                () -> Money.of(100, "USD").plus(Money.of(100, "CAD")));
    }

    @Test
    void overflowIsAnErrorNotAWrap() {
        assertThrows(ArithmeticException.class,
                () -> Money.of(Long.MAX_VALUE, "USD").plus(Money.of(1, "USD")));
    }

    @Test
    void formatsUsingTheCurrencyFractionDigits() {
        assertEquals("10.50 USD", Money.of(1050, "USD").toString());
        assertEquals("-0.05 USD", Money.of(-5, "USD").toString());
        assertEquals("500 JPY", Money.of(500, "JPY").toString());
    }

    @Test
    void comparesWithinACurrency() {
        assertTrue(Money.of(5, "CAD").compareTo(Money.of(7, "CAD")) < 0);
    }
}
