package com.taxoin.contrib;

import com.taxoin.storage.JsonStore;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * On-chain registry of available services.
 * Persists to JSON via JsonStore when storePath provided.
 */
public class ServiceRegistry {

    public static final List<String> SERVICE_TYPES = List.of(
            "sms", "gpu", "compute", "taxi", "api",
            "storage", "bandwidth", "expertise");

    /** Top-level JSON: { services: { address: ServiceRegistration } } */
    public static class ServiceStore {
        public Map<String, ServiceRegistration> services = new LinkedHashMap<>();
    }

    private final Map<String, ServiceRegistration> services = new LinkedHashMap<>();
    private final JsonStore<ServiceStore> store;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ServiceRegistry() { this(null); }

    public ServiceRegistry(Path storePath) {
        this.store = storePath != null
                ? new JsonStore<>(storePath, ServiceStore.class) : null;
        load();
    }

    private void save() {
        if (store == null) return;
        ServiceStore ss = new ServiceStore();
        ss.services.putAll(services);
        store.save(ss);
    }

    private void load() {
        if (store == null) return;
        ServiceStore ss = store.load();
        if (ss == null) return;
        services.putAll(ss.services);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean register(ServiceRegistration reg, String validatorSig) {
        lock.writeLock().lock();
        try {
            if (services.containsKey(reg.provider)) return false;
            reg.createdAt = System.currentTimeMillis() / 1000.0;
            if (validatorSig != null && !validatorSig.isEmpty()) {
                reg.attestedBy.add(validatorSig);
            }
            services.put(reg.provider, reg);
            save();
            return true;
        } finally { lock.writeLock().unlock(); }
    }

    public boolean register(ServiceRegistration reg) {
        return register(reg, null);
    }

    public List<ServiceRegistration> listServices(String serviceType, double minRating) {
        lock.readLock().lock();
        try {
            return services.values().stream()
                    .filter(s -> serviceType == null || s.serviceType.equals(serviceType))
                    .filter(s -> s.rating >= minRating)
                    .toList();
        } finally { lock.readLock().unlock(); }
    }

    public List<ServiceRegistration> listServices() {
        return listServices(null, 0.0);
    }

    public ServiceRegistration getService(String provider) {
        lock.readLock().lock();
        try { return services.get(provider); }
        finally { lock.readLock().unlock(); }
    }
}
