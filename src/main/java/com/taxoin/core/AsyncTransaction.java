package com.taxoin.core;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AsyncTransaction {
    public String sender;
    public String recipient;
    public double value;
    public int nonce;
    @JsonProperty("gas_price")  public double gasPrice  = 1.0;
    @JsonProperty("gas_limit")  public int    gasLimit  = 21000;
    public String signature = "";
    @JsonProperty("tx_hash")    public String txHash    = "";
    public double timestamp;

    public AsyncTransaction() {}

    public AsyncTransaction(String sender, String recipient,
                            double value, int nonce, double timestamp) {
        this.sender    = sender;
        this.recipient = recipient;
        this.value     = value;
        this.nonce     = nonce;
        this.timestamp = timestamp;
        this.txHash    = HashUtils.sha256(
                sender + recipient + value + nonce + timestamp);
    }

    public double totalCost() { return value + gasPrice * gasLimit; }
}
