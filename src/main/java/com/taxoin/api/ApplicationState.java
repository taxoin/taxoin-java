package com.taxoin.api;

import com.taxoin.branch.BranchManager;
import com.taxoin.contrib.ReputationTracker;
import com.taxoin.contrib.ServiceRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * CDI singleton holding all application state.
 * Equivalent to Python's module-level globals in api.py.
 */
@ApplicationScoped
public class ApplicationState {

    private static final Logger LOG = Logger.getLogger(ApplicationState.class.getName());

    @ConfigProperty(name = "taxoin.dir", defaultValue = ".taxoin")
    String taxoinDir;

    private BranchManager      manager;
    private ServiceRegistry    serviceRegistry;
    private ReputationTracker  reputation;
    private final Map<String, Double> balances = new HashMap<>();

    @PostConstruct
    void init() {
        try {
            Path dir = Paths.get(taxoinDir);
            manager         = new BranchManager(dir);
            serviceRegistry = new ServiceRegistry(dir.resolve("services.json"));
            reputation      = new ReputationTracker(dir.resolve("reputation.json"));
            LOG.info("Taxoin state initialised at " + dir.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialise Taxoin state", e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (manager != null) {
            try { manager.close(); } catch (Exception ignored) {}
        }
    }

    public BranchManager      getManager()         { return manager; }
    public ServiceRegistry    getServiceRegistry() { return serviceRegistry; }
    public ReputationTracker  getReputation()      { return reputation; }
    public Map<String, Double> getBalances()       { return balances; }

    public double getBalance(String address) {
        // Check branch state first, fallback to balances map
        try {
            var main = manager.getMainState();
            if (main != null) {
                var acct = main.accounts.get(address);
                if (acct != null) return acct.balance;
            }
        } catch (Exception ignored) {}
        return balances.getOrDefault(address, 0.0);
    }
}
