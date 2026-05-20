package com.taxoin.contrib;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ContribTest {

    // ═══════════════════════════════════════════════════════════
    // GenesisRegistry
    // ═══════════════════════════════════════════════════════════

    @Test
    void genesis_constants() {
        assertEquals(50.0, GenesisRegistry.GENESIS_REWARD, 0.001);
        assertEquals(21_000_000.0, GenesisRegistry.MAX_GENESIS_SUPPLY, 0.001);
        assertEquals(420_000, (int)(GenesisRegistry.MAX_GENESIS_SUPPLY / GenesisRegistry.GENESIS_REWARD));
    }

    @Test
    void genesis_startsWith_zeroSupply() {
        GenesisRegistry reg = new GenesisRegistry(List.of("0xv1", "0xv2", "0xv3"));
        assertEquals(0.0, reg.getTotalGenesisSupply(), 0.001);
    }

    @Test
    void genesis_oneAttestationNotComplete() {
        GenesisRegistry reg = new GenesisRegistry(List.of("0xv1", "0xv2", "0xv3"));
        reg.addAttestation("0xnewbie", "0xv1");
        assertFalse(reg.isAttestationComplete("0xnewbie"));
        assertEquals(1, reg.getAttestationCount("0xnewbie"));
    }

    @Test
    void genesis_threeAttestationsComplete() {
        GenesisRegistry reg = new GenesisRegistry(List.of("0xv1", "0xv2", "0xv3"));
        reg.addAttestation("0xnewbie", "0xv1");
        reg.addAttestation("0xnewbie", "0xv2");
        reg.addAttestation("0xnewbie", "0xv3");
        assertTrue(reg.isAttestationComplete("0xnewbie"));
        assertTrue(reg.isGenesisDone("0xnewbie"));
        assertEquals(50.0, reg.getTotalGenesisSupply(), 0.001);
    }

    @Test
    void genesis_duplicateValidatorRejected() {
        GenesisRegistry reg = new GenesisRegistry(List.of("0xv1", "0xv2", "0xv3"));
        reg.addAttestation("0xnewbie", "0xv1");
        boolean dup = reg.addAttestation("0xnewbie", "0xv1");
        assertFalse(dup);
        assertEquals(1, reg.getAttestationCount("0xnewbie"));
    }

    @Test
    void genesis_doubleGenesisRejected() {
        GenesisRegistry reg = new GenesisRegistry(List.of("0xv1", "0xv2", "0xv3"));
        reg.addAttestation("0xnewbie", "0xv1");
        reg.addAttestation("0xnewbie", "0xv2");
        reg.addAttestation("0xnewbie", "0xv3");
        double before = reg.getTotalGenesisSupply();
        reg.addAttestation("0xnewbie", "0xv1"); // already done
        assertEquals(before, reg.getTotalGenesisSupply(), 0.001);
    }

    @Test
    void genesis_persistence(@TempDir Path tmp) {
        Path path = tmp.resolve("genesis.json");
        GenesisRegistry reg = new GenesisRegistry(List.of("0xv1", "0xv2", "0xv3"), path);
        reg.addAttestation("0xalice", "0xv1");
        reg.addAttestation("0xalice", "0xv2");
        reg.addAttestation("0xalice", "0xv3");

        // Reload
        GenesisRegistry reg2 = new GenesisRegistry(List.of("0xv1", "0xv2", "0xv3"), path);
        assertTrue(reg2.isGenesisDone("0xalice"));
        assertEquals(50.0, reg2.getTotalGenesisSupply(), 0.001);
    }

    // ═══════════════════════════════════════════════════════════
    // ServiceRegistry
    // ═══════════════════════════════════════════════════════════

    @Test
    void service_registerAndGet() {
        ServiceRegistry reg = new ServiceRegistry();
        ServiceRegistration svc = new ServiceRegistration(
                "0xalice", "sms", 0.1, "SMS gateway", "https://sms.alice.com");
        assertTrue(reg.register(svc));
        ServiceRegistration loaded = reg.getService("0xalice");
        assertNotNull(loaded);
        assertEquals("sms", loaded.serviceType);
        assertEquals(0.1, loaded.pricePerUnit, 0.001);
    }

    @Test
    void service_duplicateProviderRejected() {
        ServiceRegistry reg = new ServiceRegistry();
        reg.register(new ServiceRegistration("0xalice", "sms", 0.1, "SMS", "url"));
        assertFalse(reg.register(new ServiceRegistration("0xalice", "gpu", 5.0, "GPU", "url")));
    }

    @Test
    void service_listEmpty() {
        assertTrue(new ServiceRegistry().listServices().isEmpty());
    }

    @Test
    void service_listAll() {
        ServiceRegistry reg = new ServiceRegistry();
        reg.register(new ServiceRegistration("0xalice", "sms", 0.1, "SMS", "url1"));
        reg.register(new ServiceRegistration("0xbob",   "gpu", 5.0, "GPU", "url2"));
        assertEquals(2, reg.listServices().size());
    }

    @Test
    void service_filterByType() {
        ServiceRegistry reg = new ServiceRegistry();
        reg.register(new ServiceRegistration("0xalice", "sms", 0.1, "SMS", "url"));
        reg.register(new ServiceRegistration("0xbob",   "gpu", 5.0, "GPU", "url"));
        List<ServiceRegistration> sms = reg.listServices("sms", 0.0);
        assertEquals(1, sms.size());
        assertEquals("0xalice", sms.get(0).provider);
    }

    @Test
    void service_filterByMinRating() {
        ServiceRegistry reg = new ServiceRegistry();
        ServiceRegistration s1 = new ServiceRegistration("0xalice", "sms", 0.1, "A", "url");
        s1.rating = 4.5;
        ServiceRegistration s2 = new ServiceRegistration("0xbob", "sms", 0.1, "B", "url");
        s2.rating = 2.0;
        reg.register(s1); reg.register(s2);
        List<ServiceRegistration> high = reg.listServices(null, 4.0);
        assertEquals(1, high.size());
        assertEquals("0xalice", high.get(0).provider);
    }

    @Test
    void service_validatorSigAppended() {
        ServiceRegistry reg = new ServiceRegistry();
        ServiceRegistration svc = new ServiceRegistration("0xalice", "sms", 0.1, "SMS", "url");
        reg.register(svc, "validator_sig_hex");
        assertEquals(1, reg.getService("0xalice").attestedBy.size());
    }

    @Test
    void service_persistence(@TempDir Path tmp) {
        Path path = tmp.resolve("services.json");
        ServiceRegistry reg = new ServiceRegistry(path);
        reg.register(new ServiceRegistration("0xalice", "sms", 0.1, "SMS", "url"));

        ServiceRegistry reg2 = new ServiceRegistry(path);
        assertNotNull(reg2.getService("0xalice"));
        assertEquals("sms", reg2.getService("0xalice").serviceType);
    }

    // ═══════════════════════════════════════════════════════════
    // AttestedTransaction + BalanceHold
    // ═══════════════════════════════════════════════════════════

    @Test
    void attest_validWhenBothSigs() {
        AttestedTransaction tx = new AttestedTransaction(
                "0xalice", "0xbob", "sms:0xbob", 0.1, "alice_sig", "bob_sig");
        assertTrue(tx.isValid());
    }

    @Test
    void attest_invalidMissingConsumerSig() {
        AttestedTransaction tx = new AttestedTransaction(
                "0xalice", "0xbob", "sms", 0.1, "", "bob_sig");
        assertFalse(tx.isValid());
    }

    @Test
    void attest_invalidMissingProviderSig() {
        AttestedTransaction tx = new AttestedTransaction(
                "0xalice", "0xbob", "sms", 0.1, "alice_sig", "");
        assertFalse(tx.isValid());
    }

    @Test
    void attest_invalidZeroAmount() {
        AttestedTransaction tx = new AttestedTransaction(
                "0xalice", "0xbob", "sms", 0.0, "alice_sig", "bob_sig");
        assertFalse(tx.isValid());
    }

    @Test
    void attest_txIdGenerated() {
        AttestedTransaction tx = new AttestedTransaction(
                "0xalice", "0xbob", "sms", 0.1, "a", "b");
        assertNotNull(tx.txId);
        assertEquals(16, tx.txId.length());
    }

    @Test
    void hold_createDeductsBalance() {
        Map<String, Double> bal = new HashMap<>(Map.of("0xalice", 100.0));
        BalanceHold hold = new BalanceHold(bal);
        assertTrue(hold.createHold("0xalice", 1.0, "tx1"));
        assertEquals(99.0, bal.get("0xalice"), 0.001);
        assertEquals(1.0, hold.getHeld("0xalice"), 0.001);
    }

    @Test
    void hold_insufficientBalanceFails() {
        Map<String, Double> bal = new HashMap<>(Map.of("0xalice", 0.5));
        BalanceHold hold = new BalanceHold(bal);
        assertFalse(hold.createHold("0xalice", 1.0, "tx1"));
    }

    @Test
    void hold_releaseReturnsToConsumer() {
        Map<String, Double> bal = new HashMap<>(Map.of("0xalice", 100.0));
        BalanceHold hold = new BalanceHold(bal);
        hold.createHold("0xalice", 1.0, "tx1");
        hold.releaseHold("tx1");
        assertEquals(100.0, bal.get("0xalice"), 0.001);
        assertEquals(0.0, hold.getHeld("0xalice"), 0.001);
    }

    @Test
    void hold_claimCreditProvider() {
        Map<String, Double> bal = new HashMap<>(Map.of("0xalice", 100.0, "0xbob", 10.0));
        BalanceHold hold = new BalanceHold(bal);
        hold.createHold("0xalice", 1.0, "tx1");
        hold.claimHold("tx1", "0xbob", bal);
        assertEquals(99.0, bal.get("0xalice"), 0.001);
        assertEquals(11.0, bal.get("0xbob"),   0.001);
    }

    @Test
    void hold_multipleHoldsSameConsumer() {
        Map<String, Double> bal = new HashMap<>(Map.of("0xalice", 100.0));
        BalanceHold hold = new BalanceHold(bal);
        hold.createHold("0xalice", 1.0, "tx1");
        hold.createHold("0xalice", 2.0, "tx2");
        assertEquals(3.0, hold.getHeld("0xalice"), 0.001);
        assertEquals(97.0, bal.get("0xalice"),     0.001);
    }

    @Test
    void hold_releaseNonexistentReturnsFalse() {
        Map<String, Double> bal = new HashMap<>();
        BalanceHold hold = new BalanceHold(bal);
        assertFalse(hold.releaseHold("nonexistent"));
    }

    // ═══════════════════════════════════════════════════════════
    // ReputationTracker
    // ═══════════════════════════════════════════════════════════

    @Test
    void reputation_startsAtZero() {
        assertEquals(0.0, new ReputationTracker().getRating("0xalice"), 0.001);
    }

    @Test
    void reputation_ratingIncreasesWithTx() {
        ReputationTracker rt = new ReputationTracker();
        rt.recordSuccessfulTx("0xalice");
        assertEquals(0.1, rt.getRating("0xalice"), 0.001);
        rt.recordSuccessfulTx("0xalice");
        assertEquals(0.2, rt.getRating("0xalice"), 0.001);
    }

    @Test
    void reputation_capsAt5() {
        ReputationTracker rt = new ReputationTracker();
        for (int i = 0; i < 100; i++) rt.recordSuccessfulTx("0xalice");
        assertEquals(5.0, rt.getRating("0xalice"), 0.001);
    }

    @Test
    void reputation_disputeReducesRating() {
        ReputationTracker rt = new ReputationTracker();
        for (int i = 0; i < 10; i++) rt.recordSuccessfulTx("0xalice");
        double before = rt.getRating("0xalice"); // 1.0
        rt.recordDispute("0xalice", true);
        assertEquals(before - 0.5, rt.getRating("0xalice"), 0.001);
    }

    @Test
    void reputation_innocentDisputeNoEffect() {
        ReputationTracker rt = new ReputationTracker();
        for (int i = 0; i < 10; i++) rt.recordSuccessfulTx("0xalice");
        double before = rt.getRating("0xalice");
        rt.recordDispute("0xalice", false);
        assertEquals(before, rt.getRating("0xalice"), 0.001);
    }

    @Test
    void reputation_ratingNotNegative() {
        ReputationTracker rt = new ReputationTracker();
        rt.recordSuccessfulTx("0xalice");  // 0.1
        rt.recordDispute("0xalice", true); // -0.5 → 0.0 (clamped)
        assertEquals(0.0, rt.getRating("0xalice"), 0.001);
    }

    @Test
    void reputation_leaderboard() {
        ReputationTracker rt = new ReputationTracker();
        for (int i = 0; i < 20; i++) rt.recordSuccessfulTx("0xalice");
        for (int i = 0; i < 10; i++) rt.recordSuccessfulTx("0xbob");
        var board = rt.getLeaderboard(2);
        assertEquals(2, board.size());
        assertEquals("0xalice", board.get(0).getKey());
        assertEquals("0xbob",   board.get(1).getKey());
    }

    @Test
    void reputation_persistence(@TempDir Path tmp) {
        Path path = tmp.resolve("reputation.json");
        ReputationTracker rt = new ReputationTracker(path);
        for (int i = 0; i < 5; i++) rt.recordSuccessfulTx("0xalice");

        ReputationTracker rt2 = new ReputationTracker(path);
        assertEquals(5, rt2.getSuccessfulTxCount("0xalice"));
        assertEquals(0.5, rt2.getRating("0xalice"), 0.001);
    }
}
