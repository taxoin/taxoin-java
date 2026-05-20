package com.taxoin.branch;

import com.taxoin.core.UTXO;

import java.util.*;

/**
 * Stateless cross-branch conflict detection and resolution.
 * Direct port of conflict_detector.py — all static methods, no I/O.
 */
public final class ConflictDetector {

    private ConflictDetector() {}

    // ── Enums / Value objects ─────────────────────────────────────────────────

    public enum ConflictType {
        UTXO_DOUBLE_SPEND,
        NONCE_COLLISION,
        BALANCE_MISMATCH
    }

    public enum ResolutionStrategy {
        ABORT,
        PREFER_SOURCE,
        PREFER_TARGET,
        MANUAL
    }

    public static class Conflict {
        public final ConflictType type;
        public final String detail;
        public final Object branchValue;
        public final Object mainValue;
        public final String address;    // nullable
        public final String outpoint;   // nullable, "txId:outputIndex"

        public Conflict(ConflictType type, String detail,
                        Object branchValue, Object mainValue,
                        String address, String outpoint) {
            this.type = type;
            this.detail = detail;
            this.branchValue = branchValue;
            this.mainValue = mainValue;
            this.address = address;
            this.outpoint = outpoint;
        }
    }

    public static class MergeResult {
        public final boolean success;
        public final String mergeCommit;   // nullable
        public final List<Conflict> conflicts;
        public final String message;

        public MergeResult(boolean success, String mergeCommit,
                           List<Conflict> conflicts, String message) {
            this.success = success;
            this.mergeCommit = mergeCommit;
            this.conflicts = conflicts != null ? conflicts : List.of();
            this.message = message;
        }
    }

    public static class MergeConflictError extends RuntimeException {
        public final List<Conflict> conflicts;

        public MergeConflictError(String message, List<Conflict> conflicts) {
            super(message);
            this.conflicts = conflicts;
        }
    }

    // ── Detection ─────────────────────────────────────────────────────────────

    /** UTXO double-spend: same outpoint spent in both branches. */
    public static List<Conflict> detectUtxoConflicts(BranchState branch, BranchState target) {
        List<Conflict> conflicts = new ArrayList<>();

        Set<String> doubleSpent = new HashSet<>(branch.spentUtxos);
        doubleSpent.retainAll(target.spentUtxos);

        for (String outpoint : doubleSpent) {
            UTXO utxo = branch.utxoSet.get(outpoint);
            double amount = utxo != null ? utxo.amount : 0;
            String address = utxo != null ? utxo.address : "unknown";
            String detail = String.format("UTXO %s (%s coins to %s) spent in both branches",
                    outpoint, amount, address);
            conflicts.add(new Conflict(
                    ConflictType.UTXO_DOUBLE_SPEND, detail,
                    utxo, target.utxoSet.get(outpoint),
                    null, outpoint));
        }
        return conflicts;
    }

    /** Nonce collision: same address used same nonce in both branches. */
    public static List<Conflict> detectNonceConflicts(BranchState branch, BranchState target) {
        List<Conflict> conflicts = new ArrayList<>();

        for (var entry : branch.usedNonces.entrySet()) {
            String address = entry.getKey();
            Set<Integer> branchNonces = entry.getValue();
            Set<Integer> targetNonces = target.usedNonces.get(address);
            if (targetNonces == null) continue;

            Set<Integer> collided = new HashSet<>(branchNonces);
            collided.retainAll(targetNonces);
            if (!collided.isEmpty()) {
                List<Integer> sorted = new ArrayList<>(collided);
                Collections.sort(sorted);
                String detail = String.format(
                        "Nonce collision for %s: nonces %s used in both branches",
                        address, sorted);
                conflicts.add(new Conflict(
                        ConflictType.NONCE_COLLISION, detail,
                        branchNonces, targetNonces,
                        address, null));
            }
        }
        return conflicts;
    }

    /** Balance mismatch: same address has different balance in both branches. */
    public static List<Conflict> detectBalanceConflicts(BranchState branch, BranchState target) {
        List<Conflict> conflicts = new ArrayList<>();

        Set<String> common = new HashSet<>(branch.accounts.keySet());
        common.retainAll(target.accounts.keySet());

        for (String address : common) {
            double branchBal = branch.accounts.get(address).balance;
            double targetBal = target.accounts.get(address).balance;
            if (Double.compare(branchBal, targetBal) != 0) {
                String detail = String.format(
                        "Balance mismatch for %s: %s (branch) vs %s (target)",
                        address, branchBal, targetBal);
                conflicts.add(new Conflict(
                        ConflictType.BALANCE_MISMATCH, detail,
                        branchBal, targetBal,
                        address, null));
            }
        }
        return conflicts;
    }

    /** Run all detectors. Order: UTXO > Nonce > Balance (severity). */
    public static List<Conflict> detectAll(BranchState branch, BranchState target) {
        List<Conflict> all = new ArrayList<>();
        all.addAll(detectUtxoConflicts(branch, target));
        all.addAll(detectNonceConflicts(branch, target));
        all.addAll(detectBalanceConflicts(branch, target));
        return all;
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    public static MergeResult resolve(List<Conflict> conflicts, ResolutionStrategy strategy) {
        if (conflicts.isEmpty()) {
            return new MergeResult(true, null, List.of(), "No conflicts");
        }

        return switch (strategy) {
            case ABORT -> new MergeResult(false, null, conflicts,
                    "Aborted: " + conflicts.size() + " conflict(s) detected");

            case MANUAL -> {
                String detail = conflicts.stream()
                        .map(c -> c.detail)
                        .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
                throw new MergeConflictError(
                        "Manual resolution required: " + conflicts.size()
                                + " conflict(s): " + detail, conflicts);
            }

            case PREFER_SOURCE -> new MergeResult(true, null, conflicts,
                    "Resolved " + conflicts.size() + " conflict(s) by preferring source");

            case PREFER_TARGET -> new MergeResult(true, null, conflicts,
                    "Resolved " + conflicts.size() + " conflict(s) by preferring target");
        };
    }
}
