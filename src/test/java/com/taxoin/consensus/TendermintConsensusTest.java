package com.taxoin.consensus;

import com.taxoin.branch.BranchState;
import com.taxoin.branch.ConflictDetector;
import com.taxoin.core.Account;
import com.taxoin.validator.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TendermintConsensusTest {

    ValidatorSet vset;
    ValidatorNetwork network;
    TendermintConsensus consensus;
    String proposerAddr;

    // Mock merger — always succeeds
    TendermintConsensus.BranchMerger okMerger =
            (branch, strategy) -> new ConflictDetector.MergeResult(true, "abc123", List.of(), "Merged");

    @BeforeEach
    void setup() {
        List<ValidatorNode> nodes = new ArrayList<>();
        Map<String, String> keys = new HashMap<>();

        for (int i = 0; i < 7; i++) {
            ValidatorNode.WithKey wk = ValidatorNode.generateWithPrivateKey();
            nodes.add(wk.node());
            keys.put(wk.node().address, wk.privateKeyPem());
        }

        vset = new ValidatorSet(nodes);
        network = new ValidatorNetwork(vset, keys);
        consensus = new TendermintConsensus(network);
        proposerAddr = nodes.get(0).address;
    }

    BranchState emptyState(String name) {
        return BranchState.empty(name, "parent");
    }

    // ── PROPOSE ───────────────────────────────────────────────────────────────

    @Test
    void proposeCreatesRound() {
        ConsensusRound round = consensus.propose(emptyState("branch/0xalice/001"),
                "branch/0xalice/001", proposerAddr);
        assertEquals(ConsensusStatus.PROPOSE, round.status);
        assertEquals(1, round.roundId);
        assertNotNull(round.proposal);
        assertEquals(proposerAddr, round.proposal.proposer);
    }

    @Test
    void proposalIsSigned() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        assertFalse(round.proposal.signature.isEmpty());
    }

    // ── PREVOTE ───────────────────────────────────────────────────────────────

    @Test
    void prevoteAllYesAdvancesToPrecommit() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        round = consensus.prevotePhase(round, network,
                (p, v) -> "yes", null, null);
        assertEquals(ConsensusStatus.PRECOMMIT, round.status);
        assertEquals(7, round.prevotes.size());
    }

    @Test
    void prevoteAllNoRejectsRound() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        round = consensus.prevotePhase(round, network,
                (p, v) -> "no", null, null);
        assertEquals(ConsensusStatus.REJECTED, round.status);
        assertFalse(round.result.success);
        assertTrue(round.result.message.contains("Prevote failed"));
    }

    @Test
    void prevoteBelowQuorumRejects() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        // Only 4 YES votes (need 5)
        List<String> activeAddrs = vset.getActiveValidators().stream()
                .map(v -> v.address).toList();
        round = consensus.prevotePhase(round, network,
                (p, v) -> activeAddrs.indexOf(v.address) < 4 ? "yes" : "no",
                null, null);
        assertEquals(ConsensusStatus.REJECTED, round.status);
    }

    @Test
    void prevoteExactlyQuorumAdvances() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        // Exactly 5 YES votes
        List<String> activeAddrs = vset.getActiveValidators().stream()
                .map(v -> v.address).toList();
        round = consensus.prevotePhase(round, network,
                (p, v) -> activeAddrs.indexOf(v.address) < 5 ? "yes" : "no",
                null, null);
        assertEquals(ConsensusStatus.PRECOMMIT, round.status);
    }

    // ── DEFAULT VALIDATE FN ───────────────────────────────────────────────────

    @Test
    void defaultValidateFnYesWhenNoConflicts() {
        BranchState branch = emptyState("branch");
        BranchState main   = emptyState("main");

        ConsensusRound round = consensus.propose(branch, "branch", proposerAddr);
        ValidatorNode validator = vset.getActiveValidators().get(1);

        String vote = TendermintConsensus.defaultValidateFn(
                round.proposal, validator, branch, main, network);
        assertEquals("yes", vote);
    }

    @Test
    void defaultValidateFnNoWhenConflict() {
        BranchState branch = emptyState("branch");
        BranchState main   = emptyState("main");

        // Create a balance conflict
        branch.getAccount("0xalice").balance = 90.0;
        main.getAccount("0xalice").balance   = 100.0;

        ConsensusRound round = consensus.propose(branch, "branch", proposerAddr);
        ValidatorNode validator = vset.getActiveValidators().get(1);

        String vote = TendermintConsensus.defaultValidateFn(
                round.proposal, validator, branch, main, network);
        assertEquals("no", vote);
    }

    @Test
    void defaultValidateFnNoForUnknownProposer() {
        BranchState branch = emptyState("branch");
        BranchState main   = emptyState("main");
        ValidatorNode validator = vset.getActiveValidators().get(0);

        // Proposal with unknown proposer address
        MergeProposal badProposal = new MergeProposal(
                "branch", "0xunknown", "parent", "hash", 0, 0, "sig");

        String vote = TendermintConsensus.defaultValidateFn(
                badProposal, validator, branch, main, network);
        assertEquals("no", vote);
    }

    @Test
    void prevoteUsesDefaultFnWhenNullAndStatesProvided() {
        BranchState branch = emptyState("branch");
        BranchState main   = emptyState("main");

        ConsensusRound round = consensus.propose(branch, "branch", proposerAddr);
        // null validateFn → falls back to defaultValidateFn
        round = consensus.prevotePhase(round, network, null, branch, main);
        // No conflicts, signed proposal → should achieve quorum
        assertEquals(ConsensusStatus.PRECOMMIT, round.status);
    }

    @Test
    void prevoteRejectsWhenNullFnAndNoStates() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        round = consensus.prevotePhase(round, network, null, null, null);
        assertEquals(ConsensusStatus.REJECTED, round.status);
        assertTrue(round.result.message.contains("Missing"));
    }

    // ── PRECOMMIT ─────────────────────────────────────────────────────────────

    @Test
    void precommitAllYesAdvancesToCommit() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        round = consensus.prevotePhase(round, network,
                (p, v) -> "yes", null, null);
        round = consensus.precommitPhase(round, network, null);

        assertEquals(ConsensusStatus.COMMIT, round.status);
        assertTrue(vset.isQuorumAchieved(List.copyOf(round.precommits.keySet())));
    }

    @Test
    void precommitSignaturesAreValid() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        round = consensus.prevotePhase(round, network, (p, v) -> "yes", null, null);
        round = consensus.precommitPhase(round, network, null);

        // Each precommit signature should be verifiable
        for (Map.Entry<String, String> e : round.precommits.entrySet()) {
            ValidatorNode v = vset.getValidator(e.getKey());
            assertNotNull(v);
            String data = round.proposal.branchName
                    + round.proposal.finalStateHash + round.roundId;
            assertTrue(ValidatorNode.verifySignature(data, e.getValue(), v.publicKeyPem),
                    "Precommit signature invalid for " + e.getKey());
        }
    }

    @Test
    void precommitBelowQuorumRejects() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        round = consensus.prevotePhase(round, network, (p, v) -> "yes", null, null);

        List<String> addrs = vset.getActiveValidators().stream()
                .map(vn -> vn.address).toList();
        // Only 4 precommit
        round = consensus.precommitPhase(round, network,
                (p, v) -> addrs.indexOf(v.address) < 4 ? "yes" : "no");
        assertEquals(ConsensusStatus.REJECTED, round.status);
    }

    // ── COMMIT ────────────────────────────────────────────────────────────────

    @Test
    void commitCallsMerger() {
        ConsensusRound round = consensus.propose(emptyState("branch"),
                "branch", proposerAddr);
        round = consensus.prevotePhase(round, network, (p, v) -> "yes", null, null);
        round = consensus.precommitPhase(round, network, null);

        boolean[] called = {false};
        round = consensus.commitPhase(round, (branch, strategy) -> {
            called[0] = true;
            assertEquals("branch", branch);
            assertEquals(ConflictDetector.ResolutionStrategy.PREFER_SOURCE, strategy);
            return new ConflictDetector.MergeResult(true, "sha", List.of(), "ok");
        });

        assertTrue(called[0]);
        assertEquals(ConsensusStatus.COMMIT, round.status);
        assertTrue(round.result.success);
    }

    // ── FULL ROUND ────────────────────────────────────────────────────────────

    @Test
    void runConsensusHappyPath() {
        BranchState branch = emptyState("branch/0xalice/001");
        BranchState main   = emptyState("main");

        ConsensusRound round = consensus.runConsensus(
                branch, "branch/0xalice/001", proposerAddr,
                okMerger, main, null, null);

        assertEquals(ConsensusStatus.COMMIT, round.status);
        assertNotNull(round.result);
        assertTrue(round.result.success);
    }

    @Test
    void runConsensusStopsAtPrevoteIfRejected() {
        BranchState branch = emptyState("branch");
        BranchState main   = emptyState("main");

        // Introduce conflict → all validators vote NO
        branch.getAccount("0xalice").balance = 90.0;
        main.getAccount("0xalice").balance   = 100.0;

        ConsensusRound round = consensus.runConsensus(
                branch, "branch", proposerAddr,
                okMerger, main, null, null);

        assertEquals(ConsensusStatus.REJECTED, round.status);
    }

    @Test
    void runConsensusWithCustomValidateFn() {
        BranchState branch = emptyState("branch");
        BranchState main   = emptyState("main");

        // Custom fn always votes yes regardless of conflicts
        ConsensusRound round = consensus.runConsensus(
                branch, "branch", proposerAddr,
                okMerger, main,
                (p, v) -> "yes",   // override validation
                null);

        assertEquals(ConsensusStatus.COMMIT, round.status);
    }
}
