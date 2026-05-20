package com.taxoin.contrib;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.taxoin.storage.JsonStore;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Proof of Personhood genesis coin distribution.
 *
 * Each unique human needs 3-of-N validator attestations to receive 50 Ⓣ.
 * Maximum 420,000 participants (21M / 50).
 * Persists to JSON via JsonStore when storePath is provided.
 */
public class GenesisRegistry {

    public static final double GENESIS_REWARD     = 50.0;
    public static final double MAX_GENESIS_SUPPLY = 21_000_000.0;
    public static final int    GENESIS_QUORUM     = 3;

    // ── Persistence container ─────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenesisAttestation {
        public String       address;
        public List<String> attestedBy = new ArrayList<>();
        public boolean      completed  = false;
        public double       timestamp  = 0.0;

        public GenesisAttestation() {}
        public GenesisAttestation(String address, double timestamp) {
            this.address   = address;
            this.timestamp = timestamp;
        }
    }

    /** Top-level JSON container: { attestations: {...}, totalSupply: N } */
    public static class GenesisStore {
        public Map<String, GenesisAttestation> attestations = new LinkedHashMap<>();
        public double totalSupply = 0.0;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final List<String> validators;
    private final Map<String, GenesisAttestation> attestations = new LinkedHashMap<>();
    private double totalSupply = 0.0;

    private final JsonStore<GenesisStore> store;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public GenesisRegistry(List<String> validators) {
        this(validators, null);
    }

    public GenesisRegistry(List<String> validators, Path storePath) {
        this.validators = new ArrayList<>(validators);
        this.store = storePath != null
                ? new JsonStore<>(storePath, GenesisStore.class) : null;
        load();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void save() {
        if (store == null) return;
        GenesisStore gs = new GenesisStore();
        gs.attestations.putAll(attestations);
        gs.totalSupply = totalSupply;
        store.save(gs);
    }

    private void load() {
        if (store == null) return;
        GenesisStore gs = store.load();
        if (gs == null) return;
        attestations.putAll(gs.attestations);
        totalSupply = gs.totalSupply;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean addAttestation(String address, String validator) {
        lock.writeLock().lock();
        try {
            if (totalSupply >= MAX_GENESIS_SUPPLY) return false;

            attestations.computeIfAbsent(address,
                    a -> new GenesisAttestation(a, System.currentTimeMillis() / 1000.0));

            GenesisAttestation att = attestations.get(address);
            if (att.completed)                return false;
            if (att.attestedBy.contains(validator)) return false;

            att.attestedBy.add(validator);

            if (att.attestedBy.size() >= GENESIS_QUORUM && !att.completed) {
                att.completed  = true;
                totalSupply   += GENESIS_REWARD;
            }

            save();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isAttestationComplete(String address) {
        lock.readLock().lock();
        try {
            GenesisAttestation att = attestations.get(address);
            return att != null && att.attestedBy.size() >= GENESIS_QUORUM;
        } finally { lock.readLock().unlock(); }
    }

    public int getAttestationCount(String address) {
        lock.readLock().lock();
        try {
            GenesisAttestation att = attestations.get(address);
            return att != null ? att.attestedBy.size() : 0;
        } finally { lock.readLock().unlock(); }
    }

    public boolean isGenesisDone(String address) {
        lock.readLock().lock();
        try {
            GenesisAttestation att = attestations.get(address);
            return att != null && att.completed;
        } finally { lock.readLock().unlock(); }
    }

    public double getTotalGenesisSupply() {
        lock.readLock().lock();
        try { return totalSupply; }
        finally { lock.readLock().unlock(); }
    }

    public GenesisAttestation getAttestation(String address) {
        lock.readLock().lock();
        try { return attestations.get(address); }
        finally { lock.readLock().unlock(); }
    }

    public List<String> getValidators() { return Collections.unmodifiableList(validators); }
}
