package com.taxoin.gossip;

public class GossipMessage {
    public final String messageId;
    public final GossipMessageType type;
    public final String sender;
    public final String payload;
    public final int ttl;
    public final int sequence;
    public final double timestamp;

    public GossipMessage(String messageId, GossipMessageType type, String sender,
                         String payload, int ttl, int sequence, double timestamp) {
        this.messageId = messageId;
        this.type = type;
        this.sender = sender;
        this.payload = payload;
        this.ttl = ttl;
        this.sequence = sequence;
        this.timestamp = timestamp;
    }
}
