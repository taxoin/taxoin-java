package com.taxoin.crypto;

import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilsTest {

    // ── Key generation ────────────────────────────────────────────────────────

    @Test
    void generateKeypairReturnsBothKeys() {
        KeyPair kp = CryptoUtils.generateKeypair();
        assertNotNull(kp.getPrivate());
        assertNotNull(kp.getPublic());
    }

    @Test
    void addressFormat() {
        KeyPair kp = CryptoUtils.generateKeypair();
        String addr = CryptoUtils.publicKeyToAddress(kp.getPublic());
        assertTrue(addr.startsWith("0x"), "address must start with 0x");
        assertEquals(42, addr.length(), "address must be 42 chars (0x + 40 hex)");
        assertTrue(addr.substring(2).matches("[0-9a-f]+"), "address must be lowercase hex");
    }

    @Test
    void privateAndPublicKeyGiveSameAddress() {
        KeyPair kp = CryptoUtils.generateKeypair();
        String fromPub  = CryptoUtils.publicKeyToAddress(kp.getPublic());
        String fromPriv = CryptoUtils.privateKeyToAddress(kp.getPrivate());
        assertEquals(fromPub, fromPriv);
    }

    @Test
    void twoKeypairsHaveDifferentAddresses() {
        KeyPair kp1 = CryptoUtils.generateKeypair();
        KeyPair kp2 = CryptoUtils.generateKeypair();
        assertNotEquals(
            CryptoUtils.publicKeyToAddress(kp1.getPublic()),
            CryptoUtils.publicKeyToAddress(kp2.getPublic())
        );
    }

    // ── PEM round-trip ────────────────────────────────────────────────────────

    @Test
    void privateKeyPemRoundtrip() {
        KeyPair kp = CryptoUtils.generateKeypair();
        String pem = CryptoUtils.privateKeyToPem(kp.getPrivate());
        assertTrue(pem.contains("BEGIN PRIVATE KEY"));
        PrivateKey loaded = CryptoUtils.privateKeyFromPem(pem);
        // Same address after round-trip
        assertEquals(
            CryptoUtils.privateKeyToAddress(kp.getPrivate()),
            CryptoUtils.privateKeyToAddress(loaded)
        );
    }

    @Test
    void publicKeyPemRoundtrip() {
        KeyPair kp = CryptoUtils.generateKeypair();
        String pem = CryptoUtils.publicKeyToPem(kp.getPublic());
        assertTrue(pem.contains("BEGIN PUBLIC KEY"));
        PublicKey loaded = CryptoUtils.publicKeyFromPem(pem);
        assertEquals(
            CryptoUtils.publicKeyToAddress(kp.getPublic()),
            CryptoUtils.publicKeyToAddress(loaded)
        );
    }

    // ── Sign / Verify ─────────────────────────────────────────────────────────

    @Test
    void signAndVerify() {
        KeyPair kp = CryptoUtils.generateKeypair();
        String sig = CryptoUtils.sign(kp.getPrivate(), "hello blockchain");
        assertTrue(CryptoUtils.verify(kp.getPublic(), "hello blockchain", sig));
    }

    @Test
    void verifyWrongDataReturnsFalse() {
        KeyPair kp = CryptoUtils.generateKeypair();
        String sig = CryptoUtils.sign(kp.getPrivate(), "correct data");
        assertFalse(CryptoUtils.verify(kp.getPublic(), "wrong data", sig));
    }

    @Test
    void verifyWrongKeyReturnsFalse() {
        KeyPair kp1 = CryptoUtils.generateKeypair();
        KeyPair kp2 = CryptoUtils.generateKeypair();
        String sig = CryptoUtils.sign(kp1.getPrivate(), "test");
        assertFalse(CryptoUtils.verify(kp2.getPublic(), "test", sig));
    }

    @Test
    void signaturesAreRandomized() {
        // ECDSA uses random k — same message produces different signatures
        KeyPair kp = CryptoUtils.generateKeypair();
        String sig1 = CryptoUtils.sign(kp.getPrivate(), "message");
        String sig2 = CryptoUtils.sign(kp.getPrivate(), "message");
        assertNotEquals(sig1, sig2, "ECDSA signatures should use random k");
    }

    @Test
    void signatureIsHexString() {
        KeyPair kp = CryptoUtils.generateKeypair();
        String sig = CryptoUtils.sign(kp.getPrivate(), "data");
        assertTrue(sig.matches("[0-9a-f]+"), "signature must be hex");
        assertTrue(sig.length() > 100, "DER signature should be >100 hex chars");
    }

    @Test
    void attestedTxSignatureFormat() {
        // Simulate the exact signing format used in attested transactions
        KeyPair consumer = CryptoUtils.generateKeypair();
        KeyPair provider = CryptoUtils.generateKeypair();
        String consumerAddr = CryptoUtils.publicKeyToAddress(consumer.getPublic());
        String providerAddr = CryptoUtils.publicKeyToAddress(provider.getPublic());

        // Consumer signs the service request
        String consumerData = consumerAddr + ":" + providerAddr + ":0.1:sms:" + providerAddr;
        String consumerSig = CryptoUtils.sign(consumer.getPrivate(), consumerData);
        assertTrue(CryptoUtils.verify(consumer.getPublic(), consumerData, consumerSig));

        // Provider signs the attestation
        String txId = "tx_" + System.currentTimeMillis();
        String providerData = "attest:" + txId + ":" + providerAddr;
        String providerSig = CryptoUtils.sign(provider.getPrivate(), providerData);
        assertTrue(CryptoUtils.verify(provider.getPublic(), providerData, providerSig));
    }
}
