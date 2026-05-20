package com.taxoin.core;

public class TxOutput {
    public String address;
    public double amount;

    public TxOutput() {}

    public TxOutput(String address, double amount) {
        this.address = address;
        this.amount = amount;
    }
}
