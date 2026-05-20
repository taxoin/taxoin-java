package com.taxoin.consensus;

import com.taxoin.branch.BranchState;
import com.taxoin.branch.ConflictDetector;
import com.taxoin.validator.*;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Tendermint-style consensus for merge proposals.
 *
 * Protocol: PROPOSE → PREVOTE → PRECOMMIT → COMMIT
 * Quorum required at each phase: 2f+1 (5 of 7 validators).
 *
 * Python Callable → Java BiFunction<MergeProposal, ValidatorNode, String>
 */
public class TendermintConsensus {

    private final ValidatorNetwork validatorNetwork;

    public TendermintConsensus(ValidatorNetwork validatorNetwork) {
        this.validatorNetwork = validatorNetwork;
    }

    // ── Default validation function ───────────────────────────────────────────

    /**
     * Default validator vote function.
     * Checks: (1) proposal signature valid, (2) no conflicts with main state.
     */
    public static String defaultValidateFn(
            MergeProposal proposal,
            ValidatorNode validator,
            BranchState branchState,
            BranchState mainState,
            ValidatorNetwork network) {

        // 1. Verify proposal signature
        ValidatorNode proposer = network.validatorSet.getValidator(proposal.proposer);
        if (proposer == null) return "no";

        if (proposal.signature != null && !proposal.signature.isEmpty()) {
            String proposalData = proposal.branchName + proposal.parentHash
                    + proposal.finalStateHash + proposal.transactionCount;
            boolean validSig = ValidatorNode.verifySignature(
                    proposalData, proposal.signature, proposer.publicKeyPem);
            if (!validSig) return "no";
        }

        // 2. Conflict detection
        List<ConflictDetector.Conflict> conflicts =
                ConflictDetector.detectAll(branchState, mainState);
        return conflicts.isEmpty() ? "yes" : "no";
    }

    // ── Phase 1: PROPOSE ──────────────────────────────────────────────────────

    public ConsensusRound propose(BranchState branchState,
                                   String branchName, String proposer) {
        MergeProposal proposal = validatorNetwork.proposeMerge(
                branchName, proposer, branchState);

        ConsensusRound round = new ConsensusRound(
                proposal, 1, ConsensusStatus.PROPOSE,
                System.currentTimeMillis() / 1000.0);

        validatorNetwork.broadcast(proposal);
        return round;
    }

    // ── Phase 2: PREVOTE ──────────────────────────────────────────────────────

    /**
     * Each validator votes YES/NO. Advances to PRECOMMIT if quorum reached.
     *
     * @param validateFn null → use defaultValidateFn (requires branchState + mainState)
     */
    public ConsensusRound prevotePhase(
            ConsensusRound round,
            ValidatorNetwork network,
            BiFunction<MergeProposal, ValidatorNode, String> validateFn,
            BranchState branchState,
            BranchState mainState) {

        round.status = ConsensusStatus.PREVOTE;

        // Build effective vote function
        BiFunction<MergeProposal, ValidatorNode, String> effectiveFn;
        if (validateFn != null) {
            effectiveFn = validateFn;
        } else {
            if (branchState == null || mainState == null) {
                round.status = ConsensusStatus.REJECTED;
                round.result = new ConflictDetector.MergeResult(
                        false, null, List.of(), "Missing branch state for validation");
                return round;
            }
            effectiveFn = (p, v) -> defaultValidateFn(p, v, branchState, mainState, network);
        }

        // Collect votes
        round.prevotes.putAll(network.collectVotes(round.proposal, effectiveFn));

        // Count YES votes
        List<String> yesAddresses = round.prevotes.entrySet().stream()
                .filter(e -> "yes".equals(e.getValue()))
                .map(java.util.Map.Entry::getKey)
                .toList();

        if (network.validatorSet.isQuorumAchieved(yesAddresses)) {
            round.status = ConsensusStatus.PRECOMMIT;
        } else {
            round.status = ConsensusStatus.REJECTED;
            round.result = new ConflictDetector.MergeResult(
                    false, null, List.of(),
                    "Prevote failed: " + yesAddresses.size() + " YES votes "
                            + "(need " + network.validatorSet.getQuorumSize() + ")");
        }
        return round;
    }

    // ── Phase 3: PRECOMMIT ────────────────────────────────────────────────────

    /**
     * Validators sign precommit messages. Advances to COMMIT if quorum reached.
     *
     * @param precommitFn null → all validators precommit YES
     */
    public ConsensusRound precommitPhase(
            ConsensusRound round,
            ValidatorNetwork network,
            BiFunction<MergeProposal, ValidatorNode, String> precommitFn) {

        round.status = ConsensusStatus.PRECOMMIT;

        for (ValidatorNode validator : network.validatorSet.getActiveValidators()) {
            String decision = precommitFn != null
                    ? precommitFn.apply(round.proposal, validator)
                    : "yes";

            if ("yes".equals(decision)) {
                String privPem = network.getPrivateKey(validator.address);
                if (privPem != null) {
                    String precommitData = round.proposal.branchName
                            + round.proposal.finalStateHash + round.roundId;
                    String sig = ValidatorNode.signData(precommitData, privPem);
                    round.precommits.put(validator.address, sig);
                }
            }
        }

        List<String> precommitAddresses = List.copyOf(round.precommits.keySet());

        if (network.validatorSet.isQuorumAchieved(precommitAddresses)) {
            round.status = ConsensusStatus.COMMIT;
        } else {
            round.status = ConsensusStatus.REJECTED;
            round.result = new ConflictDetector.MergeResult(
                    false, null, List.of(),
                    "Precommit failed: " + precommitAddresses.size() + " precommits "
                            + "(need " + network.validatorSet.getQuorumSize() + ")");
        }
        return round;
    }

    // ── Phase 4: COMMIT ───────────────────────────────────────────────────────

    /**
     * Execute the merge after successful consensus.
     * BranchMerger is a functional interface — injected so this module
     * doesn't depend on BranchManager (which comes in Phase 8).
     */
    public ConsensusRound commitPhase(ConsensusRound round, BranchMerger merger) {
        round.status = ConsensusStatus.COMMIT;
        ConflictDetector.MergeResult result = merger.merge(
                round.proposal.branchName,
                ConflictDetector.ResolutionStrategy.PREFER_SOURCE);
        round.result = result;
        return round;
    }

    // ── Full round ────────────────────────────────────────────────────────────

    /**
     * Run full consensus: PROPOSE → PREVOTE → PRECOMMIT → COMMIT.
     *
     * @param validateFn  null = default (checks signature + conflicts)
     * @param precommitFn null = all validators precommit
     * @param merger      executes the actual git merge on COMMIT
     */
    public ConsensusRound runConsensus(
            BranchState branchState,
            String branchName,
            String proposer,
            BranchMerger merger,
            BranchState mainState,
            BiFunction<MergeProposal, ValidatorNode, String> validateFn,
            BiFunction<MergeProposal, ValidatorNode, String> precommitFn) {

        // PROPOSE
        ConsensusRound round = propose(branchState, branchName, proposer);

        // PREVOTE
        round = prevotePhase(round, validatorNetwork, validateFn, branchState, mainState);
        if (round.status == ConsensusStatus.REJECTED) return round;

        // PRECOMMIT
        round = precommitPhase(round, validatorNetwork, precommitFn);
        if (round.status == ConsensusStatus.REJECTED) return round;

        // COMMIT
        return commitPhase(round, merger);
    }

    // ── Functional interface for the merge step ───────────────────────────────

    @FunctionalInterface
    public interface BranchMerger {
        ConflictDetector.MergeResult merge(String sourceBranch,
                                            ConflictDetector.ResolutionStrategy strategy);
    }
}
