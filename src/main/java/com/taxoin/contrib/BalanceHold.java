package com.taxoin.contrib;

import java.util.HashMap;
import java.util.Map;

/**
 * Escrow for service payments.
 * Holds consumer funds until mutual attestation is complete.
 *
 * Python dict mutation by reference → Java Map passed by reference
 * (Map objects are passed by reference, .put() mutates in place).
 */
public class BalanceHold {

    public static class HoldRecord {
        public final String txId;
        public final String consumer;
        public final double amount;
        public final double createdAt;

        public HoldRecord(String txId, String consumer, double amount) {
            this.txId      = txId;
            this.consumer  = consumer;
            this.amount    = amount;
            this.createdAt = System.currentTimeMillis() / 1000.0;
        }
    }

    private final Map<String, HoldRecord> holds    = new HashMap<>();
    private final Map<String, Double>     balances; // reference to external map

    public BalanceHold(Map<String, Double> balances) {
        this.balances = balances;
    }

    /** Reserve funds. Returns false if insufficient balance. */
    public boolean createHold(String consumer, double amount, String txId) {
        double available = balances.getOrDefault(consumer, 0.0) - getHeld(consumer);
        if (available < amount) return false;

        // Deduct from spendable balance immediately
        balances.put(consumer, balances.getOrDefault(consumer, 0.0) - amount);
        holds.put(txId, new HoldRecord(txId, consumer, amount));
        return true;
    }

    /** Total held for a consumer across all active holds. */
    public double getHeld(String consumer) {
        return holds.values().stream()
                .filter(h -> h.consumer.equals(consumer))
                .mapToDouble(h -> h.amount)
                .sum();
    }

    /** Release hold → return funds to consumer (timeout / failure). */
    public boolean releaseHold(String txId) {
        HoldRecord hold = holds.remove(txId);
        if (hold == null) return false;
        balances.merge(hold.consumer, hold.amount, Double::sum);
        return true;
    }

    /** Claim hold → credit provider (after successful mutual attestation). */
    public boolean claimHold(String txId, String provider, Map<String, Double> target) {
        HoldRecord hold = holds.remove(txId);
        if (hold == null) return false;
        // Funds already deducted from consumer at createHold time
        target.merge(provider, hold.amount, Double::sum);
        return true;
    }

    public int holdCount() { return holds.size(); }
}
