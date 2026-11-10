package io.github.ydeliorman.ledgerkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class LedgerConcurrencyTest {

    private static final int WALLETS = 8;
    private static final long START_BALANCE = 100_000;

    @Test
    void totalMoneyIsConservedUnderConcurrentTransfers() throws Exception {
        Ledger ledger = new Ledger();
        ledger.openAccount(Account.clearing("bank", "USD"));
        for (int i = 0; i < WALLETS; i++) {
            ledger.openAccount(Account.wallet("w" + i, "USD"));
            ledger.post(PostRequest.transfer("fund-" + i, "fund", "bank", "w" + i, Money.of(START_BALANCE, "USD")));
        }

        int threads = 8;
        int perThread = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            results.add(pool.submit(() -> {
                Random random = new Random(threadId);
                start.await();
                int rejected = 0;
                for (int i = 0; i < perThread; i++) {
                    int from = random.nextInt(WALLETS);
                    int to = (from + 1 + random.nextInt(WALLETS - 1)) % WALLETS;
                    long amount = 1 + random.nextInt(30_000);
                    try {
                        ledger.post(PostRequest.transfer(
                                "t" + threadId + "-" + i, "random", "w" + from, "w" + to, Money.of(amount, "USD")));
                    } catch (LedgerException.InsufficientFunds e) {
                        rejected++; // legitimate: the wallet did not have enough
                    }
                }
                return rejected;
            }));
        }

        start.countDown();
        int rejected = 0;
        for (Future<Integer> f : results) {
            rejected += f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        long total = 0;
        for (int i = 0; i < WALLETS; i++) {
            long balance = ledger.balance("w" + i).minorUnits();
            assertTrue(balance >= 0, "wallet w" + i + " went negative: " + balance);
            total += balance;
        }
        assertEquals(START_BALANCE * WALLETS, total, "money was created or destroyed");
        // Every accepted post is in the log: 8 funding transactions + the successful transfers.
        assertEquals(WALLETS + (threads * perThread - rejected), ledger.transactions().size());
    }

    @Test
    void concurrentRetriesOfTheSameKeyMoveMoneyOnce() throws Exception {
        Ledger ledger = new Ledger();
        ledger.openAccount(Account.clearing("bank", "USD"));
        ledger.openAccount(Account.wallet("a", "USD"));
        ledger.post(PostRequest.transfer("fund", "fund", "bank", "a", Money.of(1_000, "USD")));
        ledger.openAccount(Account.wallet("b", "USD"));

        PostRequest request = PostRequest.transfer("same-key", "retry storm", "a", "b", Money.of(100, "USD"));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Transaction>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> ledger.post(request)));
        }
        for (Future<Transaction> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertEquals(Money.of(900, "USD"), ledger.balance("a"));
        assertEquals(Money.of(100, "USD"), ledger.balance("b"));
        assertEquals(2, ledger.transactions().size());
    }
}
