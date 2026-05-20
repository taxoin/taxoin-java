package com.taxoin.branch;

import com.taxoin.core.Account;
import com.taxoin.core.UTXO;
import com.taxoin.mempool.Mempool;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Isolated state for a transaction branch.
 *
 * Python asyncio.Lock → ReentrantLock (virtual-thread friendly).
 * Python set[tuple[str,int]] → Set<String> with "txId:outputIndex" keys.
 */
public class BranchState {

    public final String branchName;
    public final String parentHash;

    // Account model (Ethereum-style)
    public final Map<String, Account> accounts;

    // UTXO set (Bitcoin-style). Key = "txId:outputIndex"
    public final Map<String, UTXO> utxoSet;

    // Mempool
    public final Mempool mempool;

    // Metadata
    public final double createdAt;
    public double lastUpdated;
    public int transactionCount;

    // Conflict tracking
    public final Set<String> spentUtxos;          // "txId:outputIndex"
    public final Map<String, Set<Integer>> usedNonces; // address → used nonces

    // Concurrency (Python asyncio.Lock → ReentrantLock)
    public final ReentrantLock lock = new ReentrantLock();

    public BranchState(String branchName, String parentHash,
                       Map<String, Account> accounts,
                       Map<String, UTXO> utxoSet,
                       Mempool mempool,
                       double createdAt,
                       Set<String> spentUtxos,
                       Map<String, Set<Integer>> usedNonces) {
        this.branchName = branchName;
        this.parentHash = parentHash;
        this.accounts = accounts;
        this.utxoSet = utxoSet;
        this.mempool = mempool;
        this.createdAt = createdAt;
        this.lastUpdated = createdAt;
        this.transactionCount = 0;
        this.spentUtxos = spentUtxos;
        this.usedNonces = usedNonces;
    }

    // ── Static factory ────────────────────────────────────────────────────────

    public static BranchState empty(String branchName, String parentHash) {
        double now = System.currentTimeMillis() / 1000.0;
        return new BranchState(branchName, parentHash,
                new HashMap<>(), new HashMap<>(), new Mempool(),
                now, new HashSet<>(), new HashMap<>());
    }

    // ── Clone (deep copy, fresh mempool + fresh lock) ─────────────────────────

    public BranchState clone() {
        double now = System.currentTimeMillis() / 1000.0;

        Map<String, Account> accountsCopy = new HashMap<>();
        for (var e : accounts.entrySet()) {
            Account a = e.getValue();
            accountsCopy.put(e.getKey(), new Account(a.address, a.balance, a.nonce));
        }

        Map<String, UTXO> utxoCopy = new HashMap<>();
        for (var e : utxoSet.entrySet()) {
            UTXO u = e.getValue();
            utxoCopy.put(e.getKey(), new UTXO(u.txId, u.outputIndex, u.address, u.amount));
        }

        Map<String, Set<Integer>> noncesCopy = new HashMap<>();
        for (var e : usedNonces.entrySet()) {
            noncesCopy.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        return new BranchState(branchName, parentHash,
                accountsCopy, utxoCopy,
                new Mempool(),   // fresh mempool
                now,
                new HashSet<>(spentUtxos),
                noncesCopy);
    }

    // ── Account access ────────────────────────────────────────────────────────

    /** Get account, creating with zero balance/nonce if absent. */
    public Account getAccount(String address) {
        return accounts.computeIfAbsent(address,
                a -> new Account(a, 0.0, 0));
    }

    // ── Conflict tracking ─────────────────────────────────────────────────────

    public void trackSpentUtxo(String txId, int outputIndex) {
        spentUtxos.add(utxoKey(txId, outputIndex));
    }

    public void trackUsedNonce(String address, int nonce) {
        usedNonces.computeIfAbsent(address, k -> new HashSet<>()).add(nonce);
    }

    public boolean isUtxoSpent(String txId, int outputIndex) {
        return spentUtxos.contains(utxoKey(txId, outputIndex));
    }

    public boolean isNonceUsed(String address, int nonce) {
        Set<Integer> nonces = usedNonces.get(address);
        return nonces != null && nonces.contains(nonce);
    }

    public static String utxoKey(String txId, int outputIndex) {
        return txId + ":" + outputIndex;
    }
}
