package io.github.ydeliorman.ledgerkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerTest {

    private Ledger ledger;

    @BeforeEach
    void setUp() {
        ledger = new Ledger(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        ledger.openAccount(Account.clearing("bank", "USD"));
        ledger.openAccount(Account.wallet("alice", "USD"));
        ledger.openAccount(Account.wallet("bob", "USD"));
        // Fund alice with 100.00: the bank (debit normal) is debited, her wallet credited.
        ledger.post(PostRequest.transfer("fund-alice", "deposit", "bank", "alice", Money.of(10_000, "USD")));
    }

    @Test
    void postUpdatesBalancesOnBothSides() {
        assertEquals(Money.of(10_000, "USD"), ledger.balance("alice"));
        assertEquals(Money.of(10_000, "USD"), ledger.balance("bank"));
    }

    @Test
    void transferMovesMoneyBetweenWallets() {
        // wallets are credit normal, so a debit reduces them and a credit grows them
        ledger.post(PostRequest.transfer("t1", "alice pays bob", "alice", "bob", Money.of(2_500, "USD")));
        assertEquals(Money.of(7_500, "USD"), ledger.balance("alice"));
        assertEquals(Money.of(2_500, "USD"), ledger.balance("bob"));
    }

    @Test
    void rejectsUnbalancedTransactionsWithoutChangingAnything() {
        PostRequest bad = new PostRequest("bad", "oops", List.of(
                Entry.debit("alice", Money.of(500, "USD")),
                Entry.credit("bob", Money.of(400, "USD"))));
        assertThrows(LedgerException.Unbalanced.class, () -> ledger.post(bad));
        assertEquals(Money.of(10_000, "USD"), ledger.balance("alice"));
        assertEquals(Money.zero(java.util.Currency.getInstance("USD")), ledger.balance("bob"));
        assertEquals(1, ledger.transactions().size());
    }

    @Test
    void rejectsOverdraftOnAccountsThatForbidIt() {
        assertThrows(LedgerException.InsufficientFunds.class,
                () -> ledger.post(PostRequest.transfer("t2", "too much", "alice", "bob", Money.of(10_001, "USD"))));
        assertEquals(Money.of(10_000, "USD"), ledger.balance("alice"));
    }

    @Test
    void withdrawalBringsTheWalletAndTheBankBackToZero() {
        ledger.post(PostRequest.transfer("t3", "withdrawal", "alice", "bank", Money.of(10_000, "USD")));
        assertEquals(Money.of(0, "USD"), ledger.balance("alice"));
        assertEquals(Money.of(0, "USD"), ledger.balance("bank"));
    }

    @Test
    void allowsNegativeOnClearingAccounts() {
        ledger.openAccount(Account.clearing("fees", "USD"));
        // debit the fees account (grows it), credit the bank (shrinks it below zero from 100.00 - 500.00)
        ledger.post(PostRequest.transfer("neg", "adjustment", "fees", "bank", Money.of(50_000, "USD")));
        assertEquals(Money.of(-40_000, "USD"), ledger.balance("bank"));
    }

    @Test
    void unknownAccountIsRejected() {
        assertThrows(LedgerException.UnknownAccount.class,
                () -> ledger.post(PostRequest.transfer("t4", "x", "alice", "nobody", Money.of(1, "USD"))));
    }

    @Test
    void entryCurrencyMustMatchTheAccount() {
        assertThrows(LedgerException.CurrencyMismatch.class,
                () -> ledger.post(PostRequest.transfer("t5", "x", "alice", "bob", Money.of(1, "CAD"))));
    }

    @Test
    void duplicateAccountIdIsRejected() {
        assertThrows(LedgerException.DuplicateAccount.class,
                () -> ledger.openAccount(Account.wallet("alice", "USD")));
    }

    @Test
    void entriesMustBePositive() {
        assertThrows(LedgerException.InvalidRequest.class, () -> Entry.debit("alice", Money.of(0, "USD")));
        assertThrows(LedgerException.InvalidRequest.class, () -> Entry.credit("alice", Money.of(-5, "USD")));
    }

    @Test
    void aTransactionNeedsAtLeastTwoEntries() {
        assertThrows(LedgerException.InvalidRequest.class,
                () -> new PostRequest("k", "single", List.of(Entry.debit("alice", Money.of(1, "USD")))));
    }

    @Test
    void splitTransactionsWithManyEntriesBalance() {
        // alice pays 10.00: 9.00 to bob and 1.00 fee to the bank
        ledger.post(new PostRequest("split", "payment with fee", List.of(
                Entry.debit("alice", Money.of(1_000, "USD")),
                Entry.credit("bob", Money.of(900, "USD")),
                Entry.credit("bank", Money.of(100, "USD")))));
        assertEquals(Money.of(9_000, "USD"), ledger.balance("alice"));
        assertEquals(Money.of(900, "USD"), ledger.balance("bob"));
        // bank is debit normal, so a credit lowers it from 100.00 to 99.00
        assertEquals(Money.of(9_900, "USD"), ledger.balance("bank"));
    }

    // ---- idempotency ---------------------------------------------------------------------

    @Test
    void replayingTheSameKeyReturnsTheOriginalAndMovesNoMoney() {
        PostRequest req = PostRequest.transfer("t6", "once", "alice", "bob", Money.of(1_000, "USD"));
        Transaction first = ledger.post(req);
        Transaction second = ledger.post(req);
        assertSame(first, second);
        assertEquals(Money.of(9_000, "USD"), ledger.balance("alice"));
        assertEquals(2, ledger.transactions().size());
    }

    @Test
    void reusingAKeyForADifferentRequestIsAConflict() {
        ledger.post(PostRequest.transfer("t7", "first", "alice", "bob", Money.of(1_000, "USD")));
        assertThrows(LedgerException.IdempotencyConflict.class,
                () -> ledger.post(PostRequest.transfer("t7", "other", "alice", "bob", Money.of(2_000, "USD"))));
    }

    // ---- reversal ------------------------------------------------------------------------

    @Test
    void reversalRestoresBalancesAndIsLinkedToTheOriginal() {
        Transaction tx = ledger.post(PostRequest.transfer("t8", "mistake", "alice", "bob", Money.of(4_000, "USD")));
        Transaction undo = ledger.reverse(tx.id(), "t8-undo", "undo mistake");
        assertEquals(Money.of(10_000, "USD"), ledger.balance("alice"));
        assertEquals(Money.of(0, "USD"), ledger.balance("bob"));
        assertEquals(tx.id(), undo.reversalOf().orElseThrow());
    }

    @Test
    void aTransactionCanOnlyBeReversedOnce() {
        Transaction tx = ledger.post(PostRequest.transfer("t9", "x", "alice", "bob", Money.of(100, "USD")));
        ledger.reverse(tx.id(), "t9-undo", "undo");
        assertThrows(LedgerException.NotReversible.class, () -> ledger.reverse(tx.id(), "t9-undo-2", "again"));
    }

    @Test
    void aReversalCannotBeReversed() {
        Transaction tx = ledger.post(PostRequest.transfer("t10", "x", "alice", "bob", Money.of(100, "USD")));
        Transaction undo = ledger.reverse(tx.id(), "t10-undo", "undo");
        assertThrows(LedgerException.NotReversible.class, () -> ledger.reverse(undo.id(), "t10-redo", "redo"));
    }

    @Test
    void reversalIsIdempotentToo() {
        Transaction tx = ledger.post(PostRequest.transfer("t11", "x", "alice", "bob", Money.of(100, "USD")));
        Transaction a = ledger.reverse(tx.id(), "t11-undo", "undo");
        Transaction b = ledger.reverse(tx.id(), "t11-undo", "undo");
        assertSame(a, b);
    }

    @Test
    void reversingAnUnknownTransactionFails() {
        assertThrows(LedgerException.NotReversible.class, () -> ledger.reverse("no-such-id", "k", "undo"));
    }

    // ---- history -------------------------------------------------------------------------

    @Test
    void historyIsOrderedAndFilterableByAccount() {
        ledger.post(PostRequest.transfer("a", "1", "alice", "bob", Money.of(100, "USD")));
        ledger.post(PostRequest.transfer("b", "2", "bank", "alice", Money.of(200, "USD")));
        List<Transaction> forBob = ledger.transactionsFor("bob");
        assertEquals(1, forBob.size());
        assertEquals("1", forBob.get(0).description());
        List<Transaction> all = ledger.transactions();
        for (int i = 0; i < all.size(); i++) {
            assertEquals(i + 1L, all.get(i).sequence());
        }
        assertTrue(ledger.transaction(all.get(0).id()).isPresent());
    }
}
