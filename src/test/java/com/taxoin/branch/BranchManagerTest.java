package com.taxoin.branch;

import com.taxoin.core.AsyncTransaction;
import com.taxoin.core.Block;
import com.taxoin.validator.ValidatorNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BranchManagerTest {

    @TempDir Path tmp;
    BranchManager mgr;

    @BeforeEach
    void setup() throws Exception {
        mgr = new BranchManager(tmp);
    }

    @AfterEach
    void teardown() throws Exception {
        if (mgr != null) mgr.close();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Test
    void mainBranchExistsAfterInit() throws Exception {
        List<String> branches = mgr.listBranches();
        assertTrue(branches.contains("main") || branches.contains("master"));
    }

    @Test
    void mainStateExistsAfterInit() throws Exception {
        BranchState state = mgr.getMainState();
        assertNotNull(state);
    }

    // ── Create branch ─────────────────────────────────────────────────────────

    @Test
    void createBranchReturnsCorrectName() throws Exception {
        String name = mgr.createBranch("0xalice");
        assertTrue(name.startsWith("branch/0xalice/"), "name=" + name);
    }

    @Test
    void createBranchNameContainsTimestampAndSeq() throws Exception {
        String name = mgr.createBranch("0xalice");
        // branch/0xalice/1234567890_000
        String[] parts = name.split("/");
        assertEquals(3, parts.length);
        assertTrue(parts[2].contains("_"));
        assertTrue(parts[2].endsWith("000")); // first branch, seq=0
    }

    @Test
    void createBranchStateIsInMap() throws Exception {
        String name = mgr.createBranch("0xbob");
        assertNotNull(mgr.getBranchState(name));
    }

    @Test
    void createBranchAppearsInGit() throws Exception {
        String name = mgr.createBranch("0xalice");
        assertTrue(mgr.listBranches().contains(name));
    }

    @Test
    void createMultipleBranchesHaveUniqueNames() throws Exception {
        String a = mgr.createBranch("0xalice");
        String b = mgr.createBranch("0xalice");
        assertNotEquals(a, b);
    }

    @Test
    void branchClonesParentAccounts() throws Exception {
        BranchState main = mgr.getMainState();
        main.getAccount("0xalice").balance = 100.0;

        String branch = mgr.createBranch("0xalice");
        BranchState state = mgr.getBranchState(branch);
        assertEquals(100.0, state.accounts.get("0xalice").balance);
    }

    @Test
    void branchHasFreshMempool() throws Exception {
        String branch = mgr.createBranch("0xalice");
        assertTrue(mgr.getBranchState(branch).mempool.isEmpty());
    }

    @Test
    void unknownParentThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> mgr.createBranch("0xalice", "nonexistent"));
    }

    // ── Submit tx ─────────────────────────────────────────────────────────────

    @Test
    void submitTxFailsUnknownBranch() {
        AsyncTransaction tx = new AsyncTransaction("0xalice", "0xbob", 10.0, 0, 0);
        BranchManager.TxResult r = mgr.submitTx("nonexistent", tx);
        assertFalse(r.success());
    }

    @Test
    void submitTxFailsUnknownSender() throws Exception {
        String branch = mgr.createBranch("0xalice");
        AsyncTransaction tx = new AsyncTransaction("0xunknown", "0xbob", 10.0, 0, 0);
        assertFalse(mgr.submitTx(branch, tx).success());
    }

    @Test
    void submitTxFailsInsufficientBalance() throws Exception {
        String branch = mgr.createBranch("0xalice");
        mgr.getBranchState(branch).getAccount("0xalice").balance = 5.0;

        AsyncTransaction tx = new AsyncTransaction("0xalice", "0xbob", 100.0, 0, 0);
        assertFalse(mgr.submitTx(branch, tx).success());
    }

    @Test
    void submitTxSucceeds() throws Exception {
        String branch = mgr.createBranch("0xalice");
        mgr.getBranchState(branch).getAccount("0xalice").balance = 1_000_000.0;

        AsyncTransaction tx = new AsyncTransaction("0xalice", "0xbob", 10.0, 0, 0);
        assertTrue(mgr.submitTx(branch, tx).success());
    }

    // ── Mine block ────────────────────────────────────────────────────────────

    @Test
    void mineBlockSkipPowReturnsBlock() throws Exception {
        String branch = mgr.createBranch("0xminer");
        Block block = mgr.mineBlockOnBranch(branch, "0xminer", true);
        assertNotNull(block);
        assertNotNull(block.header);
        assertFalse(block.transactions.isEmpty());
    }

    @Test
    void mineBlockCreditsCoinbaseReward() throws Exception {
        String branch = mgr.createBranch("0xminer");
        mgr.mineBlockOnBranch(branch, "0xminer", true);
        BranchState state = mgr.getBranchState(branch);
        assertEquals(50.0, state.accounts.get("0xminer").balance, 0.001);
    }

    @Test
    void mineBlockNonexistentBranchReturnsNull() throws Exception {
        Block block = mgr.mineBlockOnBranch("nonexistent", "0xminer", true);
        assertNull(block);
    }

    // ── Merge ─────────────────────────────────────────────────────────────────

    @Test
    void mergeBranchSucceedsWithNoConflicts() throws Exception {
        String branch = mgr.createBranch("0xalice");
        mgr.getBranchState(branch).getAccount("0xalice").balance = 100.0;

        ConflictDetector.MergeResult result = mgr.mergeBranch(
                branch, null, ConflictDetector.ResolutionStrategy.PREFER_SOURCE);

        assertTrue(result.success, result.message);
    }

    @Test
    void mergeTransfersAccountsToTarget() throws Exception {
        String branch = mgr.createBranch("0xalice");
        mgr.getBranchState(branch).getAccount("0xnewaccount").balance = 999.0;

        mgr.mergeBranch(branch, null, ConflictDetector.ResolutionStrategy.PREFER_SOURCE);

        BranchState main = mgr.getMainState();
        assertNotNull(main.accounts.get("0xnewaccount"));
        assertEquals(999.0, main.accounts.get("0xnewaccount").balance, 0.001);
    }

    @Test
    void mergeRemovesSourceFromBranches() throws Exception {
        String branch = mgr.createBranch("0xalice");
        mgr.mergeBranch(branch, null, ConflictDetector.ResolutionStrategy.PREFER_SOURCE);
        assertNull(mgr.getBranchState(branch));
    }

    @Test
    void mergeFailsWithConflictAndAbortStrategy() throws Exception {
        String branch = mgr.createBranch("0xalice");
        // Same address in both → balance conflict
        mgr.getBranchState(branch).getAccount("0xshared").balance = 50.0;
        mgr.getMainState().getAccount("0xshared").balance = 100.0;

        ConflictDetector.MergeResult result = mgr.mergeBranch(
                branch, null, ConflictDetector.ResolutionStrategy.ABORT);
        assertFalse(result.success);
    }

    @Test
    void mergeUnknownSourceFails() throws Exception {
        ConflictDetector.MergeResult result = mgr.mergeBranch(
                "nonexistent", null, ConflictDetector.ResolutionStrategy.PREFER_SOURCE);
        assertFalse(result.success);
        assertTrue(result.message.contains("Source"));
    }

    // ── Validator network ─────────────────────────────────────────────────────

    @Test
    void initValidatorNetworkCreatesValidators() {
        mgr.initValidatorNetwork(7);
        assertEquals(7, mgr.getValidators().size());
    }

    @Test
    void getValidatorsEmptyBeforeInit() {
        assertTrue(mgr.getValidators().isEmpty());
    }

    @Test
    void validatorsHaveValidAddresses() {
        mgr.initValidatorNetwork(7);
        for (ValidatorNode v : mgr.getValidators()) {
            assertTrue(v.address.startsWith("0x"), "address=" + v.address);
            assertEquals(42, v.address.length());
        }
    }

    // ── Run consensus ─────────────────────────────────────────────────────────

    @Test
    void runConsensusFailsWithoutValidatorNetwork() throws Exception {
        String branch = mgr.createBranch("0xalice");
        ConflictDetector.MergeResult result = mgr.runConsensus(branch);
        assertFalse(result.success);
        assertTrue(result.message.contains("not initialized"));
    }

    @Test
    void runConsensusFullRoundSucceeds() throws Exception {
        mgr.initValidatorNetwork(7);
        String branch = mgr.createBranch("0xalice");
        // No conflicts → consensus should succeed
        ConflictDetector.MergeResult result = mgr.runConsensus(branch);
        assertTrue(result.success, "Consensus failed: " + result.message);
    }

    @Test
    void runConsensusFailsForUnknownBranch() throws Exception {
        mgr.initValidatorNetwork(7);
        ConflictDetector.MergeResult result = mgr.runConsensus("nonexistent");
        assertFalse(result.success);
    }
}
