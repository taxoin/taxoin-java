package com.taxoin.core;

public class TxInput {
    public String txId;
    public int outputIndex;
    public String signature;

    public TxInput() {}

    public TxInput(String txId, int outputIndex, String signature) {
        this.txId = txId;
        this.outputIndex = outputIndex;
        this.signature = signature;
    }
}
