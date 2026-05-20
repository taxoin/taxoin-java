package com.taxoin.api.dto;

public class WalletResponse {
    public String address;
    public String privateKey;
    public String publicKey;

    public WalletResponse() {}
    public WalletResponse(String address, String privateKey, String publicKey) {
        this.address    = address;
        this.privateKey = privateKey;
        this.publicKey  = publicKey;
    }
}
