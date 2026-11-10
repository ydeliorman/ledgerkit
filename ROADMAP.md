# Roadmap

Work planned for ledgerkit, roughly in order. Sizes are honest estimates of focused hours.

## 1. Storage abstraction (about 6h)
- Extract a `LedgerStore` interface (append transaction, load by id / idempotency key, account list, balances) from `Ledger`.
- Keep the in-memory store as the reference implementation and make the existing test suite run against any store.
- Contract tests shared by all stores.

## 2. JDBC store (about 10h)
- PostgreSQL schema: `accounts`, `transactions`, `entries`, unique index on `idempotency_key`.
- Posting in one DB transaction with row locks on the touched accounts, ordered by id to avoid deadlocks.
- Integration tests with Testcontainers.

## 3. Concurrency without a global lock (about 6h)
- Per-account locking or optimistic version checks instead of one ledger-wide lock.
- Stress test that compares throughput and checks the conservation invariant.

## 4. Balance snapshots and history queries (about 5h)
- Periodic snapshots so balances and "balance as of" queries do not replay the whole log.
- Paged transaction history per account.

## 5. Multi-currency transfers (about 4h)
- Explicit conversion transactions through per-currency clearing accounts with a recorded rate.

## 6. Command line demo (about 3h)
- `ledgerkit` CLI to open accounts, post, reverse and print balances from a file-backed ledger.

## 7. Benchmarks (about 3h)
- JMH benchmarks for posting throughput and latency under contention.

## 8. Packaging (about 2h)
- Publish snapshots, add CI (build and test on JDK 17 and 21), add a CHANGELOG.

## Ideas, not scheduled
- Holds and authorisations (reserve funds, then capture or release).
- An outbox for publishing posted transactions to a message broker.
- Property-based tests for the balance invariants.
