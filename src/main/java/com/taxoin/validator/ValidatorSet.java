package com.taxoin.validator;

import java.util.*;

/**
 * Manages the set of validators and quorum calculations.
 * 7 validators, f=2 Byzantine faults, quorum = 2f+1 = 5.
 */
public class ValidatorSet {

    public static final int VALIDATOR_COUNT   = 7;
    public static final int BYZANTINE_FAULTS  = 2;
    public static final int DEFAULT_QUORUM    = 5;   // 2f+1

    private final Map<String, ValidatorNode> validators = new LinkedHashMap<>();

    public ValidatorSet() {
        // Generate default set of 7 validators
        for (int i = 0; i < VALIDATOR_COUNT; i++) {
            ValidatorNode v = ValidatorNode.generate();
            validators.put(v.address, v);
        }
    }

    public ValidatorSet(List<ValidatorNode> nodes) {
        for (ValidatorNode v : nodes) {
            validators.put(v.address, v);
        }
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    public void addValidator(ValidatorNode v) {
        validators.put(v.address, v);
    }

    public void removeValidator(String address) {
        validators.remove(address);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public ValidatorNode getValidator(String address) {
        return validators.get(address);
    }

    public List<ValidatorNode> getActiveValidators() {
        return validators.values().stream()
                .filter(v -> v.status == ValidatorStatus.ACTIVE)
                .toList();
    }

    public int getTotalVotingPower() {
        return getActiveValidators().stream()
                .mapToInt(v -> v.votingPower)
                .sum();
    }

    /**
     * Quorum size = 2f+1, f = floor((n-1)/3).
     * With 7 validators: f=2, quorum=5.
     */
    public int getQuorumSize() {
        int n = getActiveValidators().size();
        int f = Math.max(0, (n - 1) / 3);
        return 2 * f + 1;
    }

    public boolean isQuorumAchieved(List<String> voteAddresses) {
        int power = 0;
        for (String addr : voteAddresses) {
            ValidatorNode v = validators.get(addr);
            if (v != null && v.status == ValidatorStatus.ACTIVE) {
                power += v.votingPower;
            }
        }
        return power >= getQuorumSize();
    }

    public int size() { return validators.size(); }
}
