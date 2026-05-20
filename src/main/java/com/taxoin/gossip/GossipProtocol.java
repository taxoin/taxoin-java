package com.taxoin.gossip;

import com.taxoin.core.HashUtils;
import com.taxoin.validator.ValidatorNode;
import com.taxoin.validator.ValidatorSet;

import java.util.*;

/**
 * Epidemic gossip protocol for message dissemination.
 *
 * BFS-style propagation: each node forwards to `fanout` random peers.
 * Deduplication via bounded message cache. Converges in O(log N) rounds.
 */
public class GossipProtocol {

    private final ValidatorSet validatorSet;
    private final int fanout;
    private final int cacheMaxSize;

    private final Set<String> cache = new HashSet<>();
    private final Map<String, Integer> sequenceCounters = new HashMap<>();

    public GossipProtocol(ValidatorSet validatorSet, int fanout) {
        this(validatorSet, fanout, 1000);
    }

    public GossipProtocol(ValidatorSet validatorSet, int fanout, int cacheMaxSize) {
        this.validatorSet = validatorSet;
        this.fanout = fanout;
        this.cacheMaxSize = cacheMaxSize;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private int nextSequence(String sender) {
        int seq = sequenceCounters.getOrDefault(sender, 0);
        sequenceCounters.put(sender, seq + 1);
        return seq;
    }

    private static String generateId(GossipMessageType type, String sender, int sequence) {
        String raw = type.name() + ":" + sender + ":" + sequence;
        return HashUtils.sha256(raw).substring(0, 16);
    }

    private void addToCache(String messageId) {
        if (cache.size() >= cacheMaxSize) {
            // Evict one entry (Iterator remove is O(1) for HashSet)
            cache.remove(cache.iterator().next());
        }
        cache.add(messageId);
    }

    public int getCacheSize() { return cache.size(); }
    public void clearCache()  { cache.clear(); }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    /**
     * Broadcast a message through the gossip network.
     *
     * @param msgType message type
     * @param payload message content
     * @param sender  address of originating validator
     * @param ttl     max hops (default 5)
     * @return set of all addresses that received the message
     */
    public Set<String> broadcast(GossipMessageType msgType, String payload,
                                  String sender, int ttl) {
        int sequence = nextSequence(sender);
        String messageId = generateId(msgType, sender, sequence);

        if (cache.contains(messageId)) return Set.of();
        addToCache(messageId);

        List<ValidatorNode> active = validatorSet.getActiveValidators();
        Set<String> allAddresses = new HashSet<>();
        for (ValidatorNode v : active) allAddresses.add(v.address);

        Set<String> received = new HashSet<>();
        received.add(sender);

        Set<String> frontier = new HashSet<>();
        frontier.add(sender);

        int currentTtl = ttl;
        Random rng = new Random();

        while (!frontier.isEmpty() && currentTtl > 0) {
            Set<String> nextFrontier = new HashSet<>();

            for (String nodeAddr : frontier) {
                // Candidates = active nodes not yet reached (excluding self)
                List<String> candidates = new ArrayList<>(allAddresses);
                candidates.removeAll(received);
                candidates.remove(nodeAddr);

                if (candidates.isEmpty()) continue;

                // Shuffle → take up to fanout
                Collections.shuffle(candidates, rng);
                int take = Math.min(fanout, candidates.size());

                for (int i = 0; i < take; i++) {
                    String peer = candidates.get(i);
                    if (received.add(peer)) {
                        nextFrontier.add(peer);
                    }
                }
            }

            frontier = nextFrontier;
            currentTtl--;

            if (received.containsAll(allAddresses)) break;
        }

        return received;
    }
}
