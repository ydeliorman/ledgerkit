# ledgerkit

A small, dependency-free **double-entry ledger library for Java 17+**.

It models the part of a payments or wallet backend that must never be wrong: moving
money between accounts so that every transaction balances, retries are safe, and mistakes
are undone by a new transaction instead of an edit.

- **Exact money.** Amounts are `long` minor units plus a `java.util.Currency`. No floating point, overflow is an error.
- **Balanced by construction.** Debits equal credits per currency, or the post is rejected and nothing changes.
- **Atomic.** A post either fully applies or leaves every balance untouched.
- **Idempotent.** Retrying a post with the same key returns the original transaction and moves no money. Reusing a key with different entries is a conflict.
- **Append-only with reversals.** A transaction is never edited. A reversal posts the opposite entries and links back to the original.
- **Overdraft rules per account.** Customer wallets can be forbidden from going below zero; clearing accounts can go negative.
- **Thread safe.** All operations are serialised and covered by a concurrency test that checks money is conserved.

## Quick start

```java
Ledger ledger = new Ledger();

ledger.openAccount(Account.clearing("bank", "USD"));   // debit normal, may go negative
ledger.openAccount(Account.wallet("alice", "USD"));    // credit normal, cannot go negative
ledger.openAccount(Account.wallet("bob", "USD"));

// Alice deposits 100.00: debit the bank's clearing account, credit her wallet.
ledger.post(PostRequest.transfer("dep-1", "deposit", "bank", "alice", Money.of(10_000, "USD")));

// Alice pays Bob 25.00. Retrying with the same key is harmless.
PostRequest pay = PostRequest.transfer("pay-1", "alice pays bob", "alice", "bob", Money.of(2_500, "USD"));
ledger.post(pay);
ledger.post(pay);                       // returns the same transaction, no second payment

ledger.balance("alice");                // 75.00 USD
ledger.balance("bob");                  // 25.00 USD

// Undo it later with a new, linked transaction.
Transaction original = ledger.transactionsFor("bob").get(0);
ledger.reverse(original.id(), "pay-1-undo", "customer cancelled");
```

Multi-leg posts (for example a payment with a fee) are just more entries:

```java
ledger.post(new PostRequest("pay-2", "payment with fee", List.of(
        Entry.debit("alice", Money.of(1_000, "USD")),
        Entry.credit("bob",  Money.of(900,   "USD")),
        Entry.credit("bank", Money.of(100,   "USD")))));
```

## How balances work

Every account has a *normal side*. An account's balance is `normal-side entries - opposite-side entries`.
A debit-normal account (assets, clearing accounts) grows with debits; a credit-normal account
(customer wallets, liabilities) grows with credits. `Account.wallet(...)` and `Account.clearing(...)`
cover the two common cases, and the `Account` constructor lets you choose.

## Errors

All failures extend `LedgerException`: `Unbalanced`, `CurrencyMismatch`, `UnknownAccount`,
`DuplicateAccount`, `InsufficientFunds`, `IdempotencyConflict`, `NotReversible`, `InvalidRequest`.

## Build and test

```bash
mvn test
```

Requires JDK 17 or newer and Maven 3.9+. There are no runtime dependencies; JUnit 5 is used for tests only.

## Status

Early (0.1). The in-memory ledger is complete and tested. Persistence, a CLI and benchmarks are planned;
see [ROADMAP.md](ROADMAP.md).

## License

MIT, see [LICENSE](LICENSE).
