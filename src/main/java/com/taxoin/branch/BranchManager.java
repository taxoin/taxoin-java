package com.taxoin.branch;

import com.taxoin.consensus.TendermintConsensus;
import com.taxoin.core.*;
import com.taxoin.mining.ProofOfWorkMiner;
import com.taxoin.storage.JGitBlockchain;
import com.taxoin.validator.*;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages branch lifecycle: create, mine, merge, consensus.
 *
 * Python asyncio → synchronous Java (virtual-thread ready).
 * BranchManager is the top-level orchestrator that ties together:
 *   JGitBlockchain, BranchState, ConflictDetector, TendermintConsensus.
 */
public class BranchManager implements AutoCloseable {

    private final JGitBlockchain git;
    private final Map<String, BranchState> branches = new LinkedHashMap<>();
    private final Map<String, Integer> sequenceCounters = new HashMap<>(); // "wallet:ts" → seq

    private ValidatorNetwork validatorNetwork;

    // ── Construction ──────────────────────────────────────────────────────────

    public BranchManager(Path repoPath) throws IOException, GitAPIException {
        this.git = new JGitBlockchain(repoPath);
        initMainState();
    }

    private void initMainState() throws IOException, GitAPIException {
        String main = getMainBranchName();
        BranchState state = BranchState.empty(main, "0".repeat(64));
        branches.put(main, state);
    }

    private String getMainBranchName() throws IOException, GitAPIException {
        List<String> all = git.listBranches();
        if (all.contains("main"))   return "main";
        if (all.contains("master")) return "master";
        return "main";
    }

    // ── Branch naming (branch/{wallet}/{timestamp}_{seq:03d}) ─────────────────

    private int nextSequence(String wallet, int timestamp) {
        String key = wallet + ":" + timestamp;
        int seq = sequenceCounters.getOrDefault(key, -1) + 1;
        sequenceCounters.put(key, seq);
        return seq;
    }

    // ── Create branch ─────────────────────────────────────────────────────────

    public String createBranch(String wallet) throws IOException, GitAPIException {
        return createBranch(wallet, null);
    }

    public String createBranch(String wallet, String parentBranch)
            throws IOException, GitAPIException {
        if (parentBranch == null) parentBranch = getMainBranchName();

        BranchState parentState = branches.get(parentBranch);
        if (parentState == null)
            throw new IllegalArgumentException("Parent branch not found: " + parentBranch);

        int timestamp = (int)(System.currentTimeMillis() / 1000L);
        int seq = nextSequence(wallet, timestamp);
        String branchName = String.format("branch/%s/%d_%03d", wallet, timestamp, seq);

        // Clone state
        BranchState state = parentState.clone();

        // Get the actual parent hash from git (robust: fallback to zeros)
        String parentHash;
        try {
            parentHash = git.getBranchHead(parentBranch);
        } catch (Exception e) {
            parentHash = "0".repeat(64);
        }

        // Patch branch name and parentHash via new instance
        BranchState named = new BranchState(
                branchName, parentHash,
                state.accounts, state.utxoSet, state.mempool.fresh(),
                state.createdAt, state.spentUtxos, state.usedNonces);
        branches.put(branchName, named);

        // Create git branch
        git.createBranch(branchName, parentBranch);

        // Store metadata in git notes
        git.setBranchMetadata(branchName, Map.of(
                "owner",         wallet,
                "created_at",    (long) timestamp,
                "status",        "active",
                "parent_branch", parentBranch));

        return branchName;
    }

    // ── Submit transaction ────────────────────────────────────────────────────

    /**
     * Submit an async transaction to a branch mempool.
     * Returns (success, message).
     */
    public record TxResult(boolean success, String message) {}

    public TxResult submitTx(String branchName, AsyncTransaction tx) {
        BranchState state = branches.get(branchName);
        if (state == null)
            return new TxResult(false, "Branch not found: " + branchName);

        Account sender = state.accounts.get(tx.sender);
        if (sender == null)
            return new TxResult(false, "Sender not found: " + tx.sender);

        if (tx.value < 0)
            return new TxResult(false, "Negative value");
        if (tx.totalCost() > sender.balance)
            return new TxResult(false, "Insufficient balance");
        if (tx.nonce < sender.nonce)
            return new TxResult(false, "Nonce too low");

        Map<String, Object> txMap = Map.of(
                "tx_hash",   tx.txHash,
                "sender",    tx.sender,
                "recipient", tx.recipient,
                "value",     tx.value,
                "nonce",     tx.nonce);

        boolean ok = state.mempool.submit(txMap);
        return new TxResult(ok, ok ? "ok" : "failed to submit");
    }

    // ── Mine block on branch ──────────────────────────────────────────────────

    public Block mineBlockOnBranch(String branchName, String coinbaseAddress,
                                    boolean skipPow) throws IOException, GitAPIException {
        BranchState state = branches.get(branchName);
        if (state == null) return null;

        git.switchBranch(branchName);

        // Drain pending txs from mempool
        List<Map<String, Object>> pending = state.mempool.getPending(100);
        List<AsyncTransaction> accountTxs = new ArrayList<>();
        for (Map<String, Object> m : pending) {
            AsyncTransaction t = new AsyncTransaction();
            t.txHash    = (String) m.get("tx_hash");
            t.sender    = (String) m.get("sender");
            t.recipient = (String) m.get("recipient");
            t.value     = ((Number) m.get("value")).doubleValue();
            t.nonce     = ((Number) m.get("nonce")).intValue();
            accountTxs.add(t);
        }

        // Coinbase
        double reward = 50.0;
        Transaction coinbase = Transaction.coinbase(coinbaseAddress, reward);

        // State snapshot
        Map<String, Double> snap = new LinkedHashMap<>();
        for (var e : state.accounts.entrySet()) snap.put(e.getKey(), e.getValue().balance);

        // Apply txs
        for (AsyncTransaction tx : accountTxs) {
            if (!snap.containsKey(tx.sender)) continue;
            double cost = tx.totalCost();
            if (snap.get(tx.sender) < cost) continue;
            snap.merge(tx.sender,    -cost, Double::sum);
            snap.merge(tx.recipient, tx.value, Double::sum);
            snap.merge(coinbaseAddress, tx.gasPrice * tx.gasLimit, Double::sum);
        }
        snap.merge(coinbaseAddress, reward, Double::sum);

        // Build block
        String parentHash;
        try { parentHash = git.getBranchHead(branchName); }
        catch (Exception e) { parentHash = "0".repeat(64); }

        List<Transaction> allTxs = new ArrayList<>();
        allTxs.add(coinbase);
        // Convert accountTxs to Transaction stubs
        for (AsyncTransaction t : accountTxs) {
            Transaction stub = new Transaction(List.of(), List.of(
                    new TxOutput(t.recipient, t.value)), t.timestamp);
            stub.txId = t.txHash;
            allTxs.add(stub);
        }

        String merkle = HashUtils.sha256(
                allTxs.stream().map(t -> t.txId).reduce("", String::concat));

        BlockHeader header = new BlockHeader(parentHash, merkle,
                System.currentTimeMillis() / 1000.0, 1);
        Block block = new Block(header, allTxs, snap);

        if (!skipPow) {
            ProofOfWorkMiner.mine(header);
        }

        git.addBlock(block);

        // Update branch state
        for (var e : snap.entrySet()) {
            state.getAccount(e.getKey()).balance = e.getValue();
        }
        for (AsyncTransaction tx : accountTxs) {
            Account acc = state.getAccount(tx.sender);
            acc.nonce = Math.max(acc.nonce, tx.nonce + 1);
            state.trackUsedNonce(tx.sender, tx.nonce);
        }
        Set<String> confirmed = new HashSet<>();
        allTxs.forEach(t -> confirmed.add(t.txId));
        state.mempool.removeConfirmed(confirmed);
        state.transactionCount += accountTxs.size();
        state.lastUpdated = System.currentTimeMillis() / 1000.0;

        return block;
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    public List<String> listBranches() throws IOException, GitAPIException {
        return git.listBranches();
    }

    public BranchState getBranchState(String name) {
        return branches.get(name);
    }

    public BranchState getMainState() throws IOException, GitAPIException {
        return branches.get(getMainBranchName());
    }

    // ── Merge ─────────────────────────────────────────────────────────────────

    public ConflictDetector.MergeResult mergeBranch(
            String source,
            String target,
            ConflictDetector.ResolutionStrategy strategy)
            throws IOException, GitAPIException {

        if (target == null) target = getMainBranchName();

        BranchState src = branches.get(source);
        BranchState tgt = branches.get(target);

        if (src == null)
            return new ConflictDetector.MergeResult(false, null, List.of(),
                    "Source branch not found: " + source);
        if (tgt == null)
            return new ConflictDetector.MergeResult(false, null, List.of(),
                    "Target branch not found: " + target);

        // Conflict detection
        List<ConflictDetector.Conflict> conflicts = ConflictDetector.detectAll(src, tgt);
        if (!conflicts.isEmpty()) {
            ConflictDetector.MergeResult resolved =
                    ConflictDetector.resolve(conflicts, strategy);
            if (!resolved.success) return resolved;
        }

        // Git merge
        String mergeCommit;
        try {
            mergeCommit = git.mergeBranches(source, target, "ours");
        } catch (Exception e) {
            return new ConflictDetector.MergeResult(false, null, List.of(),
                    "Git merge failed: " + e.getMessage());
        }

        // Apply source state to target (PREFER_SOURCE / ABORT-with-no-conflicts)
        if (strategy != ConflictDetector.ResolutionStrategy.PREFER_TARGET) {
            for (var e : src.accounts.entrySet()) {
                Account existing = tgt.accounts.get(e.getKey());
                int maxNonce = existing != null
                        ? Math.max(existing.nonce, e.getValue().nonce)
                        : e.getValue().nonce;
                Account merged = new Account(e.getKey(), e.getValue().balance, maxNonce);
                tgt.accounts.put(e.getKey(), merged);
            }
            for (var e : src.utxoSet.entrySet()) {
                tgt.utxoSet.putIfAbsent(e.getKey(), e.getValue());
            }
            tgt.spentUtxos.addAll(src.spentUtxos);
            for (var e : src.usedNonces.entrySet()) {
                tgt.usedNonces.computeIfAbsent(e.getKey(), k -> new HashSet<>())
                        .addAll(e.getValue());
            }
        }

        tgt.transactionCount += src.transactionCount;
        tgt.lastUpdated = src.lastUpdated;
        branches.remove(source);

        return new ConflictDetector.MergeResult(true, mergeCommit, conflicts,
                "Merged '" + source + "' into '" + target + "'");
    }

    // ── Validator network ─────────────────────────────────────────────────────

    public void initValidatorNetwork(int count) {
        List<ValidatorNode> nodes = new ArrayList<>();
        Map<String, String> keys = new HashMap<>();
        for (int i = 0; i < count; i++) {
            ValidatorNode.WithKey wk = ValidatorNode.generateWithPrivateKey();
            nodes.add(wk.node());
            keys.put(wk.node().address, wk.privateKeyPem());
        }
        validatorNetwork = new ValidatorNetwork(new ValidatorSet(nodes), keys);
    }

    public List<ValidatorNode> getValidators() {
        if (validatorNetwork == null) return List.of();
        return validatorNetwork.validatorSet.getActiveValidators();
    }

    // ── Run consensus ─────────────────────────────────────────────────────────

    public ConflictDetector.MergeResult runConsensus(String branchName)
            throws IOException, GitAPIException {
        if (validatorNetwork == null)
            return new ConflictDetector.MergeResult(false, null, List.of(),
                    "Validator network not initialized");

        BranchState state = branches.get(branchName);
        if (state == null)
            return new ConflictDetector.MergeResult(false, null, List.of(),
                    "Branch not found: " + branchName);

        List<ValidatorNode> active = validatorNetwork.validatorSet.getActiveValidators();
        if (active.isEmpty())
            return new ConflictDetector.MergeResult(false, null, List.of(), "No active validators");

        String proposer = active.get(0).address;
        BranchState mainState = getMainState();

        TendermintConsensus tendermint = new TendermintConsensus(validatorNetwork);

        // BranchMerger delegates back to mergeBranch
        TendermintConsensus.BranchMerger merger = (branch, strategy) -> {
            try {
                return mergeBranch(branch, null, strategy);
            } catch (Exception e) {
                return new ConflictDetector.MergeResult(false, null, List.of(),
                        "Merge failed: " + e.getMessage());
            }
        };

        var round = tendermint.runConsensus(
                state, branchName, proposer,
                merger, mainState, null, null);

        return round.result != null
                ? round.result
                : new ConflictDetector.MergeResult(false, null, List.of(),
                        "Consensus failed: " + round.status);
    }

    @Override
    public void close() {
        git.close();
    }
}
