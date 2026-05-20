package com.taxoin.core;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public class Block {
    public BlockHeader header;
    public List<Transaction> transactions;
    @JsonProperty("state_snapshot")
    public Map<String, Double> stateSnapshot;

    public Block() {}

    public Block(BlockHeader header, List<Transaction> transactions,
                 Map<String, Double> stateSnapshot) {
        this.header = header;
        this.transactions = transactions;
        this.stateSnapshot = stateSnapshot;
    }

    public String hash() {
        return header.hash();
    }

    public static Block genesis(int difficulty) {
        Transaction genesisTx = new Transaction(List.of(), List.of(), 0.0);
        genesisTx.txId = "genesis";
        BlockHeader header = new BlockHeader(
                "0".repeat(64), "genesis", 0.0, difficulty
        );
        return new Block(header, List.of(genesisTx), Map.of());
    }
}
