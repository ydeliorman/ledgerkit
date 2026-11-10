package io.github.ydeliorman.ledgerkit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An in-memory double-entry ledger.
 *
 * <p>Every transaction is atomic and balanced: for each currency the debits equal the
 * credits, so money is never created or destroyed by a post. All methods are thread
 * safe; mutations are serialised by a single lock, which keeps invariants simple to
 * reason about. Persisting the log is left to a store (see ROADMAP.md).
 */
public final class Ledger {

    private final Clock clock;
    private final Object lock = new Object();

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    /** Balance per account in minor units, signed so that "normal side" is positive. */
    private final Map<String, Long> balances = new HashMap<>();
    private final List<Transaction> log = new ArrayList<>();
    private final Map<String, Transaction> byId = new HashMap<>();
    private final Map<String, Transaction> byIdempotencyKey = new HashMap<>();
    /** Original transaction id -> id of the transaction that reversed it. */
    private final Map<String, String> reversedBy = new HashMap<>();

    public Ledger() {
        this(Clock.systemUTC());
    }

    public Ledger(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void openAccount(Account account) {
        Objects.requireNonNull(account, "account");
        synchronized (lock) {
            if (accounts.containsKey(account.id())) {
                throw new LedgerException.DuplicateAccount(account.id());
            }
            accounts.put(account.id(), account);
            balances.put(account.id(), 0L);
        }
    }

    /** Posts a balanced transaction. Safe to retry with the same idempotency key. */
    public Transaction post(PostRequest request) {
        Objects.requireNonNull(request, "request");
        synchronized (lock) {
            return postLocked(request, Optional.empty());
        }
    }

    /**
     * Posts a transaction that exactly undoes {@code transactionId}. A transaction can be
     * reversed once, and a reversal cannot itself be reversed (post a new transaction instead).
     */
    public Transaction reverse(String transactionId, String idempotencyKey, String description) {
        Objects.requireNonNull(transactionId, "transactionId");
        synchronized (lock) {
            Transaction original = byId.get(transactionId);
            if (original == null) {
                throw new LedgerException.NotReversible("Unknown transaction: " + transactionId);
            }
            List<Entry> flipped = original.entries().stream().map(Entry::flipped).toList();
            PostRequest request = new PostRequest(idempotencyKey, description, flipped);

            Transaction replay = byIdempotencyKey.get(idempotencyKey);
            if (replay != null) {
                return replayOrConflict(replay, request);
            }
            if (original.reversalOf().isPresent()) {
                throw new LedgerException.NotReversible("A reversal cannot be reversed: " + transactionId);
            }
            if (reversedBy.containsKey(transactionId)) {
                throw new LedgerException.NotReversible("Already reversed: " + transactionId);
            }
            Transaction reversal = postLocked(request, Optional.of(transactionId));
            reversedBy.put(transactionId, reversal.id());
            return reversal;
        }
    }

    public Money balance(String accountId) {
        synchronized (lock) {
            Account account = requireAccount(accountId);
            return new Money(balances.get(accountId), account.currency());
        }
    }

    public Optional<Transaction> transaction(String transactionId) {
        synchronized (lock) {
            return Optional.ofNullable(byId.get(transactionId));
        }
    }

    /** All transactions in posting order. */
    public List<Transaction> transactions() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(log));
        }
    }

    /** Transactions that touch {@code accountId}, in posting order. */
    public List<Transaction> transactionsFor(String accountId) {
        synchronized (lock) {
            requireAccount(accountId);
            return log.stream()
                    .filter(tx -> tx.entries().stream().anyMatch(e -> e.accountId().equals(accountId)))
                    .toList();
        }
    }

    // ---- internals (caller holds the lock) ------------------------------------------------

    private Transaction postLocked(PostRequest request, Optional<String> reversalOf) {
        Transaction existing = byIdempotencyKey.get(request.idempotencyKey());
        if (existing != null) {
            return replayOrConflict(existing, request);
        }

        checkBalanced(request.entries());
        Map<String, Long> deltas = computeDeltas(request.entries());
        for (Map.Entry<String, Long> d : deltas.entrySet()) {
            Account account = accounts.get(d.getKey());
            long next = Math.addExact(balances.get(d.getKey()), d.getValue());
            if (next < 0 && !account.allowNegative()) {
                throw new LedgerException.InsufficientFunds(account.id(), next);
            }
        }

        // All checks passed: from here on nothing can fail, so the post is atomic.
        deltas.forEach((accountId, delta) -> balances.merge(accountId, delta, Math::addExact));
        Transaction tx = new Transaction(
                log.size() + 1L,
                UUID.randomUUID().toString(),
                request.idempotencyKey(),
                request.description(),
                request.entries(),
                Instant.now(clock),
                reversalOf);
        log.add(tx);
        byId.put(tx.id(), tx);
        byIdempotencyKey.put(tx.idempotencyKey(), tx);
        return tx;
    }

    private Transaction replayOrConflict(Transaction existing, PostRequest request) {
        if (existing.entries().equals(request.entries())) {
            return existing;
        }
        throw new LedgerException.IdempotencyConflict(request.idempotencyKey());
    }

    /** Debits must equal credits within every currency. Unknown accounts and currency clashes fail here. */
    private void checkBalanced(List<Entry> entries) {
        Map<java.util.Currency, long[]> totals = new HashMap<>(); // [debits, credits]
        for (Entry entry : entries) {
            Account account = requireAccount(entry.accountId());
            if (!account.currency().equals(entry.amount().currency())) {
                throw new LedgerException.CurrencyMismatch(account.currency(), entry.amount().currency());
            }
            long[] t = totals.computeIfAbsent(account.currency(), c -> new long[2]);
            int idx = entry.side() == Side.DEBIT ? 0 : 1;
            t[idx] = Math.addExact(t[idx], entry.amount().minorUnits());
        }
        for (Map.Entry<java.util.Currency, long[]> e : totals.entrySet()) {
            if (e.getValue()[0] != e.getValue()[1]) {
                throw new LedgerException.Unbalanced(e.getKey(), e.getValue()[0], e.getValue()[1]);
            }
        }
    }

    /** Net change per account, signed relative to that account's normal side. */
    private Map<String, Long> computeDeltas(List<Entry> entries) {
        Map<String, Long> deltas = new LinkedHashMap<>();
        for (Entry entry : entries) {
            Account account = accounts.get(entry.accountId());
            long signed = entry.side() == account.normalSide()
                    ? entry.amount().minorUnits()
                    : Math.negateExact(entry.amount().minorUnits());
            deltas.merge(account.id(), signed, Math::addExact);
        }
        return deltas;
    }

    private Account requireAccount(String accountId) {
        Account account = accounts.get(accountId);
        if (account == null) {
            throw new LedgerException.UnknownAccount(accountId);
        }
        return account;
    }
}
