package io.github.ydeliorman.ledgerkit;

import java.util.Objects;

/** One line of a transaction: move a positive amount into the debit or credit side of an account. */
public record Entry(String accountId, Side side, Money amount) {

    public Entry {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new LedgerException.InvalidRequest("Entry amount must be positive: " + amount);
        }
    }

    public static Entry debit(String accountId, Money amount) {
        return new Entry(accountId, Side.DEBIT, amount);
    }

    public static Entry credit(String accountId, Money amount) {
        return new Entry(accountId, Side.CREDIT, amount);
    }

    Entry flipped() {
        return new Entry(accountId, side.opposite(), amount);
    }
}
