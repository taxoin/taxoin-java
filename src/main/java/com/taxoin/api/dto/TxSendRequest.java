package com.taxoin.api.dto;

public class TxSendRequest {
    public String consumer;
    public String provider;
    public double amount;
    public String serviceRef  = "";
    public String consumerSig = "";
    public String providerSig = "";
    public String description = "";
}
