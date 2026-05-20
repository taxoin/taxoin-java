package com.taxoin.branch;

import com.taxoin.core.Account;
import com.taxoin.core.UTXO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BranchStateTest {

    BranchState state;

    @BeforeEach
    void setup() {
        state = BranchState.empty("branch/0xalice/001", "abc123");
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    void emptyStateHasCorrectName() {
        assertEquals("branch/0xalice/001", state.branchName);
        assertEquals("abc123", state.parentHash);
    }

    @Test
    void emptyStateHasZeroTxCount() {
        assertEquals(0, state.transactionCount);
    }

    // ── Account access ────────────────────────────────────────────────────────

    @Test
    void getAccountCreatesIfAbsent() {
        Account acc = state.getAccount("0xalice");
        assertNotNull(acc);
        assertEquals("0xalice", acc.address);
        assertEquals(0.0, acc.balance);
        assertEquals(0, acc.nonce);
    }

    @Test
    void getAccountReturnsSameInstance() {
        state.getAccount("0xalice").balance = 100.0;
        assertEquals(100.0, state.getAccount("0xalice").balance);
    }

    // ── UTXO tracking ─────────────────────────────────────────────────────────

    @Test
    void trackSpentUtxo() {
        state.trackSpentUtxo("tx001", 0);
        assertTrue(state.isUtxoSpent("tx001", 0));
        assertFalse(state.isUtxoSpent("tx001", 1));
        assertFalse(state.isUtxoSpent("tx002", 0));
    }

    @Test
    void utxoKeyFormat() {
        assertEquals("abc:2", BranchState.utxoKey("abc", 2));
    }

    // ── Nonce tracking ────────────────────────────────────────────────────────

    @Test
    void trackUsedNonce() {
        state.trackUsedNonce("0xalice", 0);
        state.trackUsedNonce("0xalice", 1);
        assertTrue(state.isNonceUsed("0xalice", 0));
        assertTrue(state.isNonceUsed("0xalice", 1));
        assertFalse(state.isNonceUsed("0xalice", 2));
    }

    @Test
    void nonceNotUsedForUnknownAddress() {
        assertFalse(state.isNonceUsed("0xunknown", 0));
    }

    // ── Clone ─────────────────────────────────────────────────────────────────

    @Test
    void cloneIsDeepCopy() {
        state.getAccount("0xalice").balance = 100.0;
        state.trackSpentUtxo("tx1", 0);
        state.trackUsedNonce("0xalice", 5);

        BranchState copy = state.clone();

        // Mutate original — clone must not be affected
        state.getAccount("0xalice").balance = 999.0;
        state.trackSpentUtxo("tx2", 0);
        state.trackUsedNonce("0xalice", 6);

        assertEquals(100.0, copy.accounts.get("0xalice").balance);
        assertFalse(copy.spentUtxos.contains(BranchState.utxoKey("tx2", 0)));
        assertFalse(copy.isNonceUsed("0xalice", 6));
    }

    @Test
    void cloneHasFreshMempool() {
        state.mempool.submit(java.util.Map.of("tx_hash", "x", "data", "y"));
        BranchState copy = state.clone();
        assertTrue(copy.mempool.isEmpty());
    }

    @Test
    void cloneHasFreshLock() {
        BranchState copy = state.clone();
        assertNotSame(state.lock, copy.lock);
    }

    @Test
    void cloneResetsTxCount() {
        state.transactionCount = 42;
        BranchState copy = state.clone();
        assertEquals(0, copy.transactionCount);
    }
}
