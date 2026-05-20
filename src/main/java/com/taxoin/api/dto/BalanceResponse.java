package com.taxoin.api.dto;

public class BalanceResponse {
    public String address;
    public double balance;

    public BalanceResponse() {}
    public BalanceResponse(String address, double balance) {
        this.address = address;
        this.balance = balance;
    }
}
