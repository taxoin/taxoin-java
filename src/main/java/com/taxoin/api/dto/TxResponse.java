package com.taxoin.api.dto;

public class TxResponse {
    public String txId;
    public String status;
    public double amount;
    public String consumer;
    public String provider;

    public TxResponse() {}
    public TxResponse(String txId, String status, double amount,
                      String consumer, String provider) {
        this.txId     = txId;
        this.status   = status;
        this.amount   = amount;
        this.consumer = consumer;
        this.provider = provider;
    }
}
