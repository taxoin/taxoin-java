package com.taxoin.branch;

import com.taxoin.core.Account;
import com.taxoin.core.UTXO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConflictDetectorTest {

    BranchState branch;
    BranchState main;

    @BeforeEach
    void setup() {
        branch = BranchState.empty("branch/0xalice/001", "parent");
        main   = BranchState.empty("main", "parent");

        // Seed some accounts
        branch.getAccount("0xalice").balance = 100.0;
        branch.getAccount("0xbob").balance   = 50.0;
        main.getAccount("0xalice").balance   = 100.0;
        main.getAccount("0xbob").balance     = 50.0;

        // Seed some UTXOs
        branch.utxoSet.put("tx1:0", new UTXO("tx1", 0, "0xalice", 100.0));
        main.utxoSet.put("tx1:0",   new UTXO("tx1", 0, "0xalice", 100.0));
    }

    // ── UTXO conflicts ────────────────────────────────────────────────────────

    @Test
    void noUtxoConflictWhenNoSpends() {
        assertTrue(ConflictDetector.detectUtxoConflicts(branch, main).isEmpty());
    }

    @Test
    void detectsDoubleSpend() {
        branch.trackSpentUtxo("tx1", 0);
        main.trackSpentUtxo("tx1", 0);

        List<ConflictDetector.Conflict> conflicts =
                ConflictDetector.detectUtxoConflicts(branch, main);
        assertEquals(1, conflicts.size());
        assertEquals(ConflictDetector.ConflictType.UTXO_DOUBLE_SPEND, conflicts.get(0).type);
        assertEquals("tx1:0", conflicts.get(0).outpoint);
    }

    @Test
    void noConflictWhenDifferentUtxosSpent() {
        branch.trackSpentUtxo("tx1", 0);
        main.trackSpentUtxo("tx2", 0);
        assertTrue(ConflictDetector.detectUtxoConflicts(branch, main).isEmpty());
    }

    // ── Nonce conflicts ───────────────────────────────────────────────────────

    @Test
    void noNonceConflictWhenNoNonces() {
        assertTrue(ConflictDetector.detectNonceConflicts(branch, main).isEmpty());
    }

    @Test
    void detectsNonceCollision() {
        branch.trackUsedNonce("0xalice", 0);
        main.trackUsedNonce("0xalice", 0);

        List<ConflictDetector.Conflict> conflicts =
                ConflictDetector.detectNonceConflicts(branch, main);
        assertEquals(1, conflicts.size());
        assertEquals(ConflictDetector.ConflictType.NONCE_COLLISION, conflicts.get(0).type);
        assertEquals("0xalice", conflicts.get(0).address);
    }

    @Test
    void noNonceConflictDifferentNonces() {
        branch.trackUsedNonce("0xalice", 0);
        main.trackUsedNonce("0xalice", 1);
        assertTrue(ConflictDetector.detectNonceConflicts(branch, main).isEmpty());
    }

    @Test
    void noNonceConflictDifferentAddresses() {
        branch.trackUsedNonce("0xalice", 0);
        main.trackUsedNonce("0xbob", 0);
        assertTrue(ConflictDetector.detectNonceConflicts(branch, main).isEmpty());
    }

    // ── Balance conflicts ─────────────────────────────────────────────────────

    @Test
    void noBalanceConflictWhenSame() {
        assertTrue(ConflictDetector.detectBalanceConflicts(branch, main).isEmpty());
    }

    @Test
    void detectsBalanceMismatch() {
        branch.getAccount("0xalice").balance = 90.0; // sent 10

        List<ConflictDetector.Conflict> conflicts =
                ConflictDetector.detectBalanceConflicts(branch, main);
        assertEquals(1, conflicts.size());
        assertEquals(ConflictDetector.ConflictType.BALANCE_MISMATCH, conflicts.get(0).type);
        assertEquals("0xalice", conflicts.get(0).address);
    }

    @Test
    void newAccountNotAConflict() {
        branch.getAccount("0xnew").balance = 50.0; // only in branch
        assertTrue(ConflictDetector.detectBalanceConflicts(branch, main).isEmpty());
    }

    // ── detectAll ─────────────────────────────────────────────────────────────

    @Test
    void detectAllCombinesConflicts() {
        branch.trackSpentUtxo("tx1", 0);
        main.trackSpentUtxo("tx1", 0);
        branch.trackUsedNonce("0xalice", 0);
        main.trackUsedNonce("0xalice", 0);
        branch.getAccount("0xbob").balance = 40.0;

        List<ConflictDetector.Conflict> all = ConflictDetector.detectAll(branch, main);
        assertEquals(3, all.size());
        // Order: UTXO, Nonce, Balance
        assertEquals(ConflictDetector.ConflictType.UTXO_DOUBLE_SPEND, all.get(0).type);
        assertEquals(ConflictDetector.ConflictType.NONCE_COLLISION,   all.get(1).type);
        assertEquals(ConflictDetector.ConflictType.BALANCE_MISMATCH,  all.get(2).type);
    }

    @Test
    void detectAllEmptyWhenNoConflicts() {
        assertTrue(ConflictDetector.detectAll(branch, main).isEmpty());
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    @Test
    void resolveNoConflictsSucceeds() {
        ConflictDetector.MergeResult result =
                ConflictDetector.resolve(List.of(), ConflictDetector.ResolutionStrategy.ABORT);
        assertTrue(result.success);
        assertEquals("No conflicts", result.message);
    }

    @Test
    void resolveAbortFails() {
        branch.getAccount("0xalice").balance = 90.0;
        List<ConflictDetector.Conflict> conflicts =
                ConflictDetector.detectBalanceConflicts(branch, main);

        ConflictDetector.MergeResult result =
                ConflictDetector.resolve(conflicts, ConflictDetector.ResolutionStrategy.ABORT);
        assertFalse(result.success);
        assertTrue(result.message.contains("Aborted"));
    }

    @Test
    void resolvePreferSourceSucceeds() {
        branch.getAccount("0xalice").balance = 90.0;
        List<ConflictDetector.Conflict> conflicts =
                ConflictDetector.detectBalanceConflicts(branch, main);

        ConflictDetector.MergeResult result =
                ConflictDetector.resolve(conflicts, ConflictDetector.ResolutionStrategy.PREFER_SOURCE);
        assertTrue(result.success);
        assertTrue(result.message.contains("source"));
    }

    @Test
    void resolvePreferTargetSucceeds() {
        branch.getAccount("0xalice").balance = 90.0;
        List<ConflictDetector.Conflict> conflicts =
                ConflictDetector.detectBalanceConflicts(branch, main);

        ConflictDetector.MergeResult result =
                ConflictDetector.resolve(conflicts, ConflictDetector.ResolutionStrategy.PREFER_TARGET);
        assertTrue(result.success);
        assertTrue(result.message.contains("target"));
    }

    @Test
    void resolveManualThrows() {
        branch.getAccount("0xalice").balance = 90.0;
        List<ConflictDetector.Conflict> conflicts =
                ConflictDetector.detectBalanceConflicts(branch, main);

        assertThrows(ConflictDetector.MergeConflictError.class, () ->
                ConflictDetector.resolve(conflicts, ConflictDetector.ResolutionStrategy.MANUAL));
    }
}
