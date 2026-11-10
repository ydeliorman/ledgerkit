package io.github.ydeliorman.ledgerkit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * An immutable, posted transaction. {@code sequence} is the position in the ledger's
 * total order, starting at 1. {@code reversalOf} is set when this transaction undoes another.
 */
public record Transaction(
        long sequence,
        String id,
        String idempotencyKey,
        String description,
        List<Entry> entries,
        Instant postedAt,
        Optional<String> reversalOf) {

    public Transaction {
        entries = List.copyOf(entries);
    }
}
