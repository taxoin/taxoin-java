package com.taxoin.contrib;

import com.taxoin.core.HashUtils;

public class AttestedTransaction {

    public static final int TX_TIMEOUT_BLOCKS = 10;

    public String consumer;
    public String provider;
    public String serviceRef;
    public double amount;
    public String consumerSig;
    public String providerSig;
    public double timestamp;
    public String description;
    public String txId;

    public AttestedTransaction() {}

    public AttestedTransaction(String consumer, String provider,
                                String serviceRef, double amount,
                                String consumerSig, String providerSig) {
        this.consumer    = consumer;
        this.provider    = provider;
        this.serviceRef  = serviceRef;
        this.amount      = amount;
        this.consumerSig = consumerSig != null ? consumerSig : "";
        this.providerSig = providerSig != null ? providerSig : "";
        this.timestamp   = System.currentTimeMillis() / 1000.0;
        this.description = "";
        // tx_id: SHA256(consumer:provider:amount:timestamp)[:16]
        String raw = consumer + ":" + provider + ":" + amount + ":" + this.timestamp;
        this.txId = HashUtils.sha256(raw).substring(0, 16);
    }

    /** Valid only when both signatures present and amount > 0. */
    public boolean isValid() {
        return consumerSig != null && !consumerSig.isEmpty()
                && providerSig != null && !providerSig.isEmpty()
                && amount > 0.0;
    }
}
