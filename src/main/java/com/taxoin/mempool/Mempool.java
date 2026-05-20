package com.taxoin.mempool;

import com.taxoin.core.Account;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Pending transaction pool.
 * Python asyncio.Queue → LinkedBlockingQueue (thread-safe).
 */
public class Mempool {

    public static final int MAX_SIZE = 1000;

    // Simplified: store transactions as maps (AsyncTransaction → Map for now)
    private final LinkedBlockingQueue<Map<String, Object>> queue =
            new LinkedBlockingQueue<>(MAX_SIZE);
    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    public boolean submit(Map<String, Object> tx) {
        String txHash = (String) tx.get("tx_hash");
        if (txHash != null && seen.contains(txHash)) return false;
        if (queue.offer(tx)) {
            if (txHash != null) seen.add(txHash);
            return true;
        }
        return false;
    }

    public List<Map<String, Object>> getPending(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        queue.drainTo(result, limit);
        return result;
    }

    public void removeConfirmed(Collection<String> txHashes) {
        seen.removeAll(txHashes);
    }

    public int size() { return queue.size(); }

    public boolean isEmpty() { return queue.isEmpty(); }

    public Mempool fresh() { return new Mempool(); }
}
