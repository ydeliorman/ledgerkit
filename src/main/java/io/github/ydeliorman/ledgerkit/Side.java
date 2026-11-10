package io.github.ydeliorman.ledgerkit;

/** Which side of the books an entry or an account's normal balance sits on. */
public enum Side {
    DEBIT,
    CREDIT;

    public Side opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
