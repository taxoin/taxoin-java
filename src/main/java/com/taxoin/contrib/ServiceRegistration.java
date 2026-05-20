package com.taxoin.contrib;

import java.util.ArrayList;
import java.util.List;

public class ServiceRegistration {
    public String       provider;
    public String       serviceType;
    public double       pricePerUnit;
    public String       description;
    public String       endpoint;
    public List<String> attestedBy  = new ArrayList<>();
    public double       rating      = 0.0;
    public int          totalTx     = 0;
    public double       createdAt   = 0.0;

    public ServiceRegistration() {}

    public ServiceRegistration(String provider, String serviceType,
                                double pricePerUnit, String description,
                                String endpoint) {
        this.provider     = provider;
        this.serviceType  = serviceType;
        this.pricePerUnit = pricePerUnit;
        this.description  = description;
        this.endpoint     = endpoint;
    }
}
