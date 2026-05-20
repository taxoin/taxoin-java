package com.taxoin.contrib;

import com.taxoin.storage.JsonStore;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Tracks provider reputation: successful_tx, disputes, rating.
 *
 * Rating formula (matches Python implementation):
 *   base    = min(5.0, successful_tx * 0.1)   // 0.1 per tx, cap 5.0
 *   penalty = disputes * 0.5
 *   rating  = max(0.0, base - penalty)
 */
public class ReputationTracker {

    // ── Data classes ──────────────────────────────────────────────────────────

    public static class ReputationRecord {
        public String address;
        public int    successfulTx = 0;
        public int    disputes     = 0;
        public double rating       = 0.0;

        public ReputationRecord() {}
        public ReputationRecord(String address) { this.address = address; }
    }

    public static class ReputationStore {
        public Map<String, ReputationRecord> records = new LinkedHashMap<>();
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final Map<String, ReputationRecord> records = new LinkedHashMap<>();
    private final JsonStore<ReputationStore> store;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ReputationTracker() { this(null); }

    public ReputationTracker(Path storePath) {
        this.store = storePath != null
                ? new JsonStore<>(storePath, ReputationStore.class) : null;
        load();
    }

    private void save() {
        if (store == null) return;
        ReputationStore rs = new ReputationStore();
        rs.records.putAll(records);
        store.save(rs);
    }

    private void load() {
        if (store == null) return;
        ReputationStore rs = store.load();
        if (rs == null) return;
        records.putAll(rs.records);
    }

    private double recalculate(String address) {
        ReputationRecord rec = records.get(address);
        if (rec == null || rec.successfulTx == 0) return 0.0;
        double base    = Math.min(5.0, rec.successfulTx * 0.1);
        double penalty = rec.disputes * 0.5;
        return Math.max(0.0, base - penalty);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void recordSuccessfulTx(String address) {
        lock.writeLock().lock();
        try {
            records.computeIfAbsent(address, ReputationRecord::new).successfulTx++;
            records.get(address).rating = recalculate(address);
            save();
        } finally { lock.writeLock().unlock(); }
    }

    public void recordDispute(String address, boolean guilty) {
        if (!guilty) return;
        lock.writeLock().lock();
        try {
            records.computeIfAbsent(address, ReputationRecord::new).disputes++;
            records.get(address).rating = recalculate(address);
            save();
        } finally { lock.writeLock().unlock(); }
    }

    public double getRating(String address) {
        lock.readLock().lock();
        try {
            ReputationRecord r = records.get(address);
            return r != null ? r.rating : 0.0;
        } finally { lock.readLock().unlock(); }
    }

    public int getDisputeCount(String address) {
        lock.readLock().lock();
        try {
            ReputationRecord r = records.get(address);
            return r != null ? r.disputes : 0;
        } finally { lock.readLock().unlock(); }
    }

    public int getSuccessfulTxCount(String address) {
        lock.readLock().lock();
        try {
            ReputationRecord r = records.get(address);
            return r != null ? r.successfulTx : 0;
        } finally { lock.readLock().unlock(); }
    }

    /** Top-N providers by rating, descending. */
    public List<Map.Entry<String, Double>> getLeaderboard(int topN) {
        lock.readLock().lock();
        try {
            return records.values().stream()
                    .sorted(Comparator.comparingDouble((ReputationRecord r) -> r.rating).reversed())
                    .limit(topN)
                    .map(r -> Map.entry(r.address, r.rating))
                    .toList();
        } finally { lock.readLock().unlock(); }
    }
}
