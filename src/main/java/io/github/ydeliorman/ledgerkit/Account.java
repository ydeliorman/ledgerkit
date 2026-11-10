package io.github.ydeliorman.ledgerkit;

import java.util.Currency;
import java.util.Objects;

/**
 * A ledger account in a single currency.
 *
 * <p>{@code normalSide} decides the sign of the reported balance: an asset account
 * (normal side DEBIT) grows with debits, a liability or income account (normal side
 * CREDIT) grows with credits. {@code allowNegative} lets an account, such as a
 * clearing account, go below zero; customer wallets normally keep it {@code false}.
 */
public record Account(String id, Currency currency, Side normalSide, boolean allowNegative) {

    public Account {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(normalSide, "normalSide");
        if (id.isBlank()) {
            throw new LedgerException.InvalidRequest("Account id must not be blank");
        }
    }

    /** A customer wallet style account: credit normal, cannot go negative. */
    public static Account wallet(String id, String currencyCode) {
        return new Account(id, Currency.getInstance(currencyCode), Side.CREDIT, false);
    }

    /** A clearing or system account: debit normal, may go negative. */
    public static Account clearing(String id, String currencyCode) {
        return new Account(id, Currency.getInstance(currencyCode), Side.DEBIT, true);
    }
}
