package com.taxoin.api.dto;

public class ServiceResponse {
    public String provider;
    public String serviceType;
    public double pricePerUnit;
    public String description;
    public double rating;
    public int    totalTx;

    public ServiceResponse() {}
    public ServiceResponse(String provider, String serviceType, double pricePerUnit,
                           String description, double rating, int totalTx) {
        this.provider     = provider;
        this.serviceType  = serviceType;
        this.pricePerUnit = pricePerUnit;
        this.description  = description;
        this.rating       = rating;
        this.totalTx      = totalTx;
    }
}
