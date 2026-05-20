package com.taxoin.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class BlockHeader {
    @JsonProperty("parent_hash")  public String parentHash;
    @JsonProperty("merkle_root")  public String merkleRoot;
    public double timestamp;
    public int difficulty;
    public int nonce;
    public int version;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public BlockHeader() { this.version = 1; }

    public BlockHeader(String parentHash, String merkleRoot,
                       double timestamp, int difficulty) {
        this.parentHash = parentHash;
        this.merkleRoot = merkleRoot;
        this.timestamp = timestamp;
        this.difficulty = difficulty;
        this.nonce = 0;
        this.version = 1;
    }

    public String hash() {
        try {
            return HashUtils.doubleSha256(MAPPER.writeValueAsString(this));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean meetsDifficulty() {
        String h = hash();
        for (int i = 0; i < difficulty; i++) {
            if (h.charAt(i) != '0') return false;
        }
        return true;
    }
}
