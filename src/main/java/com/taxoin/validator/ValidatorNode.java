package com.taxoin.validator;

import com.taxoin.crypto.CryptoUtils;

import java.security.KeyPair;

/**
 * A single validator node with identity and voting power.
 * Wraps CryptoUtils for signing and verification.
 */
public class ValidatorNode {

    public final String address;
    public final String publicKeyPem;
    public int votingPower;
    public ValidatorStatus status;
    public double lastSeen;

    public ValidatorNode(String address, String publicKeyPem) {
        this.address = address;
        this.publicKeyPem = publicKeyPem;
        this.votingPower = 1;
        this.status = ValidatorStatus.ACTIVE;
        this.lastSeen = 0.0;
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    public static ValidatorNode generate() {
        KeyPair kp = CryptoUtils.generateKeypair();
        String address = CryptoUtils.publicKeyToAddress(kp.getPublic());
        String pubPem = CryptoUtils.publicKeyToPem(kp.getPublic());
        return new ValidatorNode(address, pubPem);
    }

    public record WithKey(ValidatorNode node, String privateKeyPem) {}

    public static WithKey generateWithPrivateKey() {
        KeyPair kp = CryptoUtils.generateKeypair();
        String address = CryptoUtils.publicKeyToAddress(kp.getPublic());
        String pubPem = CryptoUtils.publicKeyToPem(kp.getPublic());
        String privPem = CryptoUtils.privateKeyToPem(kp.getPrivate());
        return new WithKey(new ValidatorNode(address, pubPem), privPem);
    }

    // ── Sign / Verify (PEM-based, same interface as Python) ───────────────────

    public static String signData(String data, String privateKeyPem) {
        return CryptoUtils.sign(CryptoUtils.privateKeyFromPem(privateKeyPem), data);
    }

    public static boolean verifySignature(String data, String signatureHex, String publicKeyPem) {
        try {
            return CryptoUtils.verify(CryptoUtils.publicKeyFromPem(publicKeyPem), data, signatureHex);
        } catch (Exception e) {
            return false;
        }
    }
}
