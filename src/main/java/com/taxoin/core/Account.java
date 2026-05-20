package com.taxoin.core;

public class Account {
    public String address;
    public double balance;
    public int nonce;

    public Account() {}

    public Account(String address, double balance, int nonce) {
        this.address = address;
        this.balance = balance;
        this.nonce = nonce;
    }
}
