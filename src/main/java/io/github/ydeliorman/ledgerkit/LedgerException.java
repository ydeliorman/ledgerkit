package io.github.ydeliorman.ledgerkit;

import java.util.Currency;

/** Base type for every failure the ledger can report. */
public class LedgerException extends RuntimeException {

    public LedgerException(String message) {
        super(message);
    }

    /** The entries of a transaction do not add up: debits and credits differ. */
    public static final class Unbalanced extends LedgerException {
        public Unbalanced(Currency currency, long debits, long credits) {
            super("Unbalanced " + currency.getCurrencyCode() + ": debits=" + debits + " credits=" + credits);
        }
    }

    /** Two amounts, or an amount and an account, use different currencies. */
    public static final class CurrencyMismatch extends LedgerException {
        public CurrencyMismatch(Currency expected, Currency actual) {
            super("Currency mismatch: expected " + expected.getCurrencyCode()
                    + " but got " + actual.getCurrencyCode());
        }
    }

    /** An entry refers to an account that was never opened. */
    public static final class UnknownAccount extends LedgerException {
        public UnknownAccount(String accountId) {
            super("Unknown account: " + accountId);
        }
    }

    /** An account was opened twice with the same id. */
    public static final class DuplicateAccount extends LedgerException {
        public DuplicateAccount(String accountId) {
            super("Account already exists: " + accountId);
        }
    }

    /** Posting would push an account that forbids it below zero. */
    public static final class InsufficientFunds extends LedgerException {
        public InsufficientFunds(String accountId, long balance) {
            super("Insufficient funds on " + accountId + " (balance would become " + balance + ")");
        }
    }

    /** The idempotency key was already used for a different request. */
    public static final class IdempotencyConflict extends LedgerException {
        public IdempotencyConflict(String key) {
            super("Idempotency key reused with a different request: " + key);
        }
    }

    /** The transaction to reverse does not exist, or was already reversed. */
    public static final class NotReversible extends LedgerException {
        public NotReversible(String message) {
            super(message);
        }
    }

    /** The request itself is malformed (too few entries, non positive amount, ...). */
    public static final class InvalidRequest extends LedgerException {
        public InvalidRequest(String message) {
            super(message);
        }
    }
}
