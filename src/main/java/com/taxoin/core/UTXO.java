package com.taxoin.core;

public class UTXO {
    public String txId;
    public int outputIndex;
    public String address;
    public double amount;

    public UTXO() {}

    public UTXO(String txId, int outputIndex, String address, double amount) {
        this.txId = txId;
        this.outputIndex = outputIndex;
        this.address = address;
        this.amount = amount;
    }

    public String outpointKey() {
        return txId + ":" + outputIndex;
    }
}
