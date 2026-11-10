package io.github.ydeliorman.ledgerkit;

import java.util.List;
import java.util.Objects;

/**
 * A request to post one balanced transaction.
 *
 * <p>The {@code idempotencyKey} makes retries safe: posting the same key with the same
 * entries again returns the original transaction instead of moving money twice.
 */
public record PostRequest(String idempotencyKey, String description, List<Entry> entries) {

    public PostRequest {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(entries, "entries");
        if (idempotencyKey.isBlank()) {
            throw new LedgerException.InvalidRequest("Idempotency key must not be blank");
        }
        if (entries.size() < 2) {
            throw new LedgerException.InvalidRequest("A transaction needs at least two entries");
        }
        entries = List.copyOf(entries);
    }

    /** Convenience for the most common case: move {@code amount} from one account to another. */
    public static PostRequest transfer(String idempotencyKey, String description,
                                       String fromAccountId, String toAccountId, Money amount) {
        return new PostRequest(idempotencyKey, description,
                List.of(Entry.debit(fromAccountId, amount), Entry.credit(toAccountId, amount)));
    }
}
