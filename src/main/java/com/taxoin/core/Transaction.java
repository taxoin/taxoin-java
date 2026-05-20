package com.taxoin.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;

public class Transaction {
    public List<TxInput> inputs;
    public List<TxOutput> outputs;
    @JsonProperty("tx_id")
    public String txId;
    public double timestamp;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public Transaction() {}

    public Transaction(List<TxInput> inputs, List<TxOutput> outputs, double timestamp) {
        this.inputs = inputs;
        this.outputs = outputs;
        this.timestamp = timestamp;
        this.txId = computeHash();
    }

    private String computeHash() {
        try {
            String raw = MAPPER.writeValueAsString(this);
            return HashUtils.doubleSha256(raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Transaction coinbase(String address, double reward) {
        return new Transaction(
                List.of(),
                List.of(new TxOutput(address, reward)),
                System.currentTimeMillis() / 1000.0
        );
    }
}
