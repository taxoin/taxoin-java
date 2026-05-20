package com.taxoin.crypto;

import com.taxoin.core.HashUtils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Base64;

/**
 * Cryptographic utilities: secp256k1 ECDSA key generation, signing, verification.
 *
 * Wire-compatible with Python's `cryptography` library:
 * - Private keys: PKCS8 PEM  ("-----BEGIN PRIVATE KEY-----")
 * - Public keys:  SPKI PEM   ("-----BEGIN PUBLIC KEY-----")
 * - Signatures:   DER hex    (SHA256withECDSA)
 * - Addresses:    0x + last 40 hex chars of SHA256(uncompressed_pub_key)
 */
public final class CryptoUtils {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CryptoUtils() {}

    // ── Key generation ────────────────────────────────────────────────────────

    public static KeyPair generateKeypair() {
        try {
            ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "BC");
            kpg.initialize(spec, new SecureRandom());
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Key generation failed", e);
        }
    }

    // ── PEM serialization ─────────────────────────────────────────────────────

    public static String privateKeyToPem(PrivateKey key) {
        byte[] encoded = key.getEncoded(); // PKCS8
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                           .encodeToString(encoded);
        return "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n";
    }

    public static String publicKeyToPem(PublicKey key) {
        byte[] encoded = key.getEncoded(); // SPKI / X.509
        String b64 = Base64.getMimeEncoder(64, new byte[]{'\n'})
                           .encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" + b64 + "\n-----END PUBLIC KEY-----\n";
    }

    public static PrivateKey privateKeyFromPem(String pem) {
        try {
            String b64 = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(b64);
            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load private key from PEM", e);
        }
    }

    public static PublicKey publicKeyFromPem(String pem) {
        try {
            String b64 = pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(b64);
            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            return kf.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load public key from PEM", e);
        }
    }

    // ── Address derivation ────────────────────────────────────────────────────

    /**
     * Derive address from public key.
     * Formula: "0x" + SHA256(uncompressed_pub_bytes)[-40:]
     * Identical to Python: sha256(X962/UncompressedPoint).hexdigest()[-40:]
     */
    public static String publicKeyToAddress(PublicKey pub) {
        byte[] uncompressed = uncompressedPublicKeyBytes(pub);
        String sha = HashUtils.sha256(new String(uncompressed, StandardCharsets.ISO_8859_1));

        // Actually hash the raw bytes, not the string
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(uncompressed);
            String hex = HashUtils.bytesToHex(hash);
            return "0x" + hex.substring(hex.length() - 40);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String privateKeyToAddress(PrivateKey priv) {
        try {
            KeyFactory kf = KeyFactory.getInstance("EC", "BC");
            ECPublicKey pub = derivePublicKey(priv);
            return publicKeyToAddress(pub);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── Sign / Verify ─────────────────────────────────────────────────────────

    /**
     * Sign data with private key. Returns DER-encoded signature as hex.
     * Compatible with Python: private_key.sign(data, ECDSA(SHA256)).hex()
     */
    public static String sign(PrivateKey privateKey, String data) {
        try {
            Signature sig = Signature.getInstance("SHA256withECDSA", "BC");
            sig.initSign(privateKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return HashUtils.bytesToHex(sig.sign());
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }

    /**
     * Verify DER hex signature against public key and data.
     * Compatible with Python: public_key.verify(bytes.fromhex(sig), data, ECDSA(SHA256))
     */
    public static boolean verify(PublicKey publicKey, String data, String signatureHex) {
        try {
            Signature sig = Signature.getInstance("SHA256withECDSA", "BC");
            sig.initVerify(publicKey);
            sig.update(data.getBytes(StandardCharsets.UTF_8));
            return sig.verify(hexToBytes(signatureHex));
        } catch (SignatureException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Verification failed", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Get uncompressed EC point bytes (0x04 || x || y), 65 bytes total. */
    public static byte[] uncompressedPublicKeyBytes(PublicKey pub) {
        ECPublicKey ecPub = (ECPublicKey) pub;
        ECPoint point = ecPub.getW();
        byte[] x = toBytes32(point.getAffineX());
        byte[] y = toBytes32(point.getAffineY());
        byte[] result = new byte[65];
        result[0] = 0x04;
        System.arraycopy(x, 0, result, 1, 32);
        System.arraycopy(y, 0, result, 33, 32);
        return result;
    }

    private static byte[] toBytes32(BigInteger n) {
        byte[] raw = n.toByteArray();
        if (raw.length == 32) return raw;
        byte[] out = new byte[32];
        if (raw.length > 32) {
            // Remove leading zero byte (sign byte)
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            // Pad with leading zeros
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    private static ECPublicKey derivePublicKey(PrivateKey priv) throws Exception {
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");
        ECPrivateKeySpec spec = kf.getKeySpec(priv, ECPrivateKeySpec.class);
        // Derive public key from private key via BC
        org.bouncycastle.jce.interfaces.ECPrivateKey bcPriv =
            (org.bouncycastle.jce.interfaces.ECPrivateKey) priv;
        org.bouncycastle.math.ec.ECPoint pubPoint =
            bcPriv.getParameters().getG().multiply(bcPriv.getD()).normalize();
        byte[] encoded = pubPoint.getEncoded(false);
        // Re-create as Java PublicKey
        org.bouncycastle.jce.spec.ECPublicKeySpec pubSpec =
            new org.bouncycastle.jce.spec.ECPublicKeySpec(pubPoint, bcPriv.getParameters());
        return (ECPublicKey) kf.generatePublic(pubSpec);
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }
}
