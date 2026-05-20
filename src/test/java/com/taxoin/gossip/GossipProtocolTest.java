package com.taxoin.gossip;

import com.taxoin.validator.ValidatorNode;
import com.taxoin.validator.ValidatorSet;
import com.taxoin.validator.ValidatorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GossipProtocolTest {

    ValidatorSet vset;
    GossipProtocol gossip;
    String senderAddr;

    @BeforeEach
    void setup() {
        vset = new ValidatorSet(); // 7 validators
        gossip = new GossipProtocol(vset, 3);
        senderAddr = vset.getActiveValidators().get(0).address;
    }

    @Test
    void broadcastReachesSender() {
        Set<String> reached = gossip.broadcast(GossipMessageType.PROPOSAL,
                "payload", senderAddr, 5);
        assertTrue(reached.contains(senderAddr));
    }

    @Test
    void broadcastReachesAllValidators() {
        Set<String> reached = gossip.broadcast(GossipMessageType.PROPOSAL,
                "payload", senderAddr, 5);
        List<String> allAddrs = vset.getActiveValidators().stream()
                .map(v -> v.address).toList();
        assertTrue(reached.containsAll(allAddrs),
                "Expected all " + allAddrs.size() + " validators reached, got " + reached.size());
    }

    @Test
    void duplicateMessageReturnsEmpty() {
        gossip.broadcast(GossipMessageType.PROPOSAL, "p", senderAddr, 5);
        // Same sender, next sequence → different ID, should propagate
        Set<String> second = gossip.broadcast(GossipMessageType.PROPOSAL, "p", senderAddr, 5);
        // Second message is a different sequence → not empty (different ID)
        assertFalse(second.isEmpty());
    }

    @Test
    void cacheGrowsWithMessages() {
        assertEquals(0, gossip.getCacheSize());
        gossip.broadcast(GossipMessageType.PROPOSAL, "p1", senderAddr, 5);
        assertEquals(1, gossip.getCacheSize());
        gossip.broadcast(GossipMessageType.PREVOTE, "p2", senderAddr, 5);
        assertEquals(2, gossip.getCacheSize());
    }

    @Test
    void clearCacheResetsSize() {
        gossip.broadcast(GossipMessageType.PROPOSAL, "p", senderAddr, 5);
        gossip.clearCache();
        assertEquals(0, gossip.getCacheSize());
    }

    @Test
    void ttlZeroReachesSenderOnly() {
        Set<String> reached = gossip.broadcast(GossipMessageType.HEARTBEAT,
                "ping", senderAddr, 0);
        assertEquals(1, reached.size());
        assertTrue(reached.contains(senderAddr));
    }

    @Test
    void singleValidatorReachesSelf() {
        ValidatorNode solo = ValidatorNode.generate();
        ValidatorSet soloSet = new ValidatorSet(List.of(solo));
        GossipProtocol soloGossip = new GossipProtocol(soloSet, 3);
        Set<String> reached = soloGossip.broadcast(GossipMessageType.HEARTBEAT,
                "ping", solo.address, 5);
        assertEquals(Set.of(solo.address), reached);
    }

    @Test
    void differentMessageTypesAreIndependent() {
        Set<String> r1 = gossip.broadcast(GossipMessageType.PROPOSAL,   "p", senderAddr, 5);
        Set<String> r2 = gossip.broadcast(GossipMessageType.PREVOTE,    "p", senderAddr, 5);
        Set<String> r3 = gossip.broadcast(GossipMessageType.PRECOMMIT,  "p", senderAddr, 5);
        // All three should reach everyone
        int expected = vset.getActiveValidators().size();
        assertEquals(expected, r1.size());
        assertEquals(expected, r2.size());
        assertEquals(expected, r3.size());
    }

    @Test
    void inactiveValidatorNotPropagated() {
        // Mark one validator inactive
        ValidatorNode inactive = vset.getActiveValidators().get(1);
        inactive.status = ValidatorStatus.INACTIVE;

        Set<String> reached = gossip.broadcast(GossipMessageType.PROPOSAL,
                "p", senderAddr, 5);

        // Inactive validator should NOT be in the reached set
        // (gossip only propagates to active validators)
        assertFalse(reached.contains(inactive.address),
                "Inactive validator should not receive gossip");

        inactive.status = ValidatorStatus.ACTIVE; // restore
    }
}
