package com.taxoin.validator;

import com.taxoin.branch.BranchState;
import com.taxoin.core.HashUtils;
import com.taxoin.gossip.GossipMessageType;
import com.taxoin.gossip.GossipProtocol;

import java.util.*;
import java.util.function.BiFunction;

/**
 * In-process validator network simulating message passing.
 * Python Callable → Java BiFunction<MergeProposal, ValidatorNode, String>.
 */
public class ValidatorNetwork {

    public final ValidatorSet validatorSet;
    private final Map<String, String> privateKeys; // address → PEM
    private GossipProtocol gossip;                 // lazy init

    public ValidatorNetwork(ValidatorSet validatorSet, Map<String, String> privateKeys) {
        this.validatorSet = validatorSet;
        this.privateKeys = privateKeys != null ? privateKeys : new HashMap<>();
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    /** Broadcast a proposal — returns list of addresses that acknowledged. */
    public List<String> broadcast(MergeProposal proposal) {
        return validatorSet.getActiveValidators().stream()
                .map(v -> v.address)
                .toList();
    }

    // ── Vote collection ───────────────────────────────────────────────────────

    /**
     * Collect votes from all active validators.
     * Python: validate_fn(proposal, validator) → "yes"/"no"
     * Java:   BiFunction<MergeProposal, ValidatorNode, String>
     */
    public Map<String, String> collectVotes(
            MergeProposal proposal,
            BiFunction<MergeProposal, ValidatorNode, String> validateFn) {
        Map<String, String> votes = new LinkedHashMap<>();
        for (ValidatorNode v : validatorSet.getActiveValidators()) {
            votes.put(v.address, validateFn.apply(proposal, v));
        }
        return votes;
    }

    // ── Propose merge ─────────────────────────────────────────────────────────

    /**
     * Create a signed merge proposal from a branch state.
     * State hash = SHA256(accounts + utxoSet + spentUtxos + usedNonces).
     */
    public MergeProposal proposeMerge(String branchName, String proposer,
                                       BranchState branchState) {
        String stateData = branchState.accounts.toString()
                + branchState.utxoSet.toString()
                + branchState.spentUtxos.toString()
                + branchState.usedNonces.toString();
        String finalStateHash = HashUtils.sha256(stateData);

        MergeProposal proposal = new MergeProposal(
                branchName, proposer,
                branchState.parentHash,
                finalStateHash,
                branchState.transactionCount,
                System.currentTimeMillis() / 1000.0,
                ""
        );

        // Sign if we hold the private key
        String privPem = privateKeys.get(proposer);
        if (privPem != null) {
            String proposalData = proposal.branchName + proposal.parentHash
                    + proposal.finalStateHash + proposal.transactionCount;
            proposal.signature = ValidatorNode.signData(proposalData, privPem);
        }

        return proposal;
    }

    // ── Gossip ────────────────────────────────────────────────────────────────

    public Set<String> gossipBroadcast(GossipMessageType msgType,
                                        String payload, String sender) {
        if (gossip == null) {
            gossip = new GossipProtocol(validatorSet, 3);
        }
        return gossip.broadcast(msgType, payload, sender, 5);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public String getPrivateKey(String address) {
        return privateKeys.get(address);
    }

    public void addPrivateKey(String address, String privPem) {
        privateKeys.put(address, privPem);
    }
}
