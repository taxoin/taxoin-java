package com.taxoin.api.dto;

public class StatusResponse {
    public String network     = "taxoin";
    public String status      = "running";
    public int    validators  = 0;
    public int    blocks      = 0;
    public int    services    = 0;
    public double totalSupply = 0.0;
}
