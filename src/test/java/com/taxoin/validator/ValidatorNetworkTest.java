package com.taxoin.validator;

import com.taxoin.branch.BranchState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ValidatorNetworkTest {

    ValidatorSet vset;
    Map<String, String> privateKeys;
    ValidatorNetwork network;

    @BeforeEach
    void setup() {
        // Build 7 validators with their private keys
        List<ValidatorNode> nodes = new ArrayList<>();
        privateKeys = new HashMap<>();

        for (int i = 0; i < 7; i++) {
            ValidatorNode.WithKey wk = ValidatorNode.generateWithPrivateKey();
            nodes.add(wk.node());
            privateKeys.put(wk.node().address, wk.privateKeyPem());
        }

        vset = new ValidatorSet(nodes);
        network = new ValidatorNetwork(vset, privateKeys);
    }

    // ── ValidatorNode ─────────────────────────────────────────────────────────

    @Test
    void generateNodeHasAddress() {
        ValidatorNode v = ValidatorNode.generate();
        assertTrue(v.address.startsWith("0x"));
        assertEquals(42, v.address.length());
    }

    @Test
    void generateWithPrivateKeyReturnsBoth() {
        ValidatorNode.WithKey wk = ValidatorNode.generateWithPrivateKey();
        assertNotNull(wk.node());
        assertNotNull(wk.privateKeyPem());
        assertTrue(wk.privateKeyPem().contains("BEGIN PRIVATE KEY"));
    }

    @Test
    void signAndVerify() {
        ValidatorNode.WithKey wk = ValidatorNode.generateWithPrivateKey();
        String sig = ValidatorNode.signData("hello", wk.privateKeyPem());
        assertTrue(ValidatorNode.verifySignature("hello", sig, wk.node().publicKeyPem));
    }

    @Test
    void verifyWrongDataReturnsFalse() {
        ValidatorNode.WithKey wk = ValidatorNode.generateWithPrivateKey();
        String sig = ValidatorNode.signData("correct", wk.privateKeyPem());
        assertFalse(ValidatorNode.verifySignature("wrong", sig, wk.node().publicKeyPem));
    }

    @Test
    void verifyInvalidHexReturnsFalse() {
        ValidatorNode v = ValidatorNode.generate();
        assertFalse(ValidatorNode.verifySignature("data", "not-hex", v.publicKeyPem));
    }

    // ── ValidatorSet ──────────────────────────────────────────────────────────

    @Test
    void defaultSetHasSevenValidators() {
        ValidatorSet def = new ValidatorSet();
        assertEquals(7, def.size());
    }

    @Test
    void allDefaultValidatorsAreActive() {
        ValidatorSet def = new ValidatorSet();
        assertEquals(7, def.getActiveValidators().size());
    }

    @Test
    void quorumSizeIsCorrect() {
        // 7 validators: f=2, quorum=5
        assertEquals(5, vset.getQuorumSize());
    }

    @Test
    void quorumSizeScalesWithActiveCount() {
        ValidatorSet small = new ValidatorSet(List.of(
                ValidatorNode.generate(), ValidatorNode.generate(), ValidatorNode.generate()
        ));
        // 3 validators: f=0, quorum=1
        assertEquals(1, small.getQuorumSize());
    }

    @Test
    void isQuorumAchievedWithFiveVotes() {
        List<String> five = vset.getActiveValidators().subList(0, 5).stream()
                .map(v -> v.address).toList();
        assertTrue(vset.isQuorumAchieved(five));
    }

    @Test
    void isQuorumNotAchievedWithFourVotes() {
        List<String> four = vset.getActiveValidators().subList(0, 4).stream()
                .map(v -> v.address).toList();
        assertFalse(vset.isQuorumAchieved(four));
    }

    @Test
    void addAndRemoveValidator() {
        ValidatorNode extra = ValidatorNode.generate();
        vset.addValidator(extra);
        assertEquals(8, vset.size());
        vset.removeValidator(extra.address);
        assertEquals(7, vset.size());
    }

    @Test
    void inactiveValidatorNotCountedInQuorum() {
        ValidatorNode v = vset.getActiveValidators().get(0);
        v.status = ValidatorStatus.INACTIVE;
        // Now 6 active: f=1, quorum=3
        assertEquals(3, vset.getQuorumSize());
        v.status = ValidatorStatus.ACTIVE; // restore
    }

    // ── ValidatorNetwork ──────────────────────────────────────────────────────

    @Test
    void broadcastReturnsAllActiveAddresses() {
        MergeProposal proposal = new MergeProposal(
                "branch/0xalice/001", "0xval1",
                "abc", "def", 0, 0, "");
        List<String> acked = network.broadcast(proposal);
        assertEquals(7, acked.size());
    }

    @Test
    void collectVotesAllYes() {
        MergeProposal proposal = new MergeProposal(
                "branch", "proposer", "p", "s", 0, 0, "");
        Map<String, String> votes = network.collectVotes(proposal,
                (p, v) -> "yes");
        assertEquals(7, votes.size());
        assertTrue(votes.values().stream().allMatch("yes"::equals));
    }

    @Test
    void collectVotesAllNo() {
        MergeProposal proposal = new MergeProposal(
                "branch", "proposer", "p", "s", 0, 0, "");
        Map<String, String> votes = network.collectVotes(proposal,
                (p, v) -> "no");
        assertTrue(votes.values().stream().allMatch("no"::equals));
    }

    @Test
    void proposeMergeCreatesSignedProposal() {
        String proposerAddr = vset.getActiveValidators().get(0).address;
        BranchState state = BranchState.empty("branch/0xalice/001", "parent");
        state.transactionCount = 3;

        MergeProposal proposal = network.proposeMerge("branch/0xalice/001",
                proposerAddr, state);

        assertEquals("branch/0xalice/001", proposal.branchName);
        assertEquals(proposerAddr, proposal.proposer);
        assertEquals("parent", proposal.parentHash);
        assertEquals(3, proposal.transactionCount);
        assertNotNull(proposal.finalStateHash);
        assertFalse(proposal.signature.isEmpty()); // signed because we have the key
    }

    @Test
    void proposeMergeUnsignedWithoutKey() {
        BranchState state = BranchState.empty("branch", "parent");
        MergeProposal proposal = network.proposeMerge("branch",
                "0xunknown-address", state);
        assertTrue(proposal.signature.isEmpty());
    }
}
