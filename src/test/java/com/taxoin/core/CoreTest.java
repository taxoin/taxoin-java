package com.taxoin.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoreTest {

    @Test
    void sha256Deterministic() {
        assertEquals(HashUtils.sha256("hello"), HashUtils.sha256("hello"));
        assertNotEquals(HashUtils.sha256("hello"), HashUtils.sha256("world"));
    }

    @Test
    void doubleSha256() {
        String h = HashUtils.doubleSha256("taxoin");
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]+"));
    }

    @Test
    void accountDefaults() {
        Account a = new Account("0xalice", 100.0, 0);
        assertEquals("0xalice", a.address);
        assertEquals(100.0, a.balance);
        assertEquals(0, a.nonce);
    }

    @Test
    void utxoOutpointKey() {
        UTXO u = new UTXO("abc123", 0, "0xalice", 50.0);
        assertEquals("abc123:0", u.outpointKey());
    }

    @Test
    void transactionCoinbase() {
        Transaction tx = Transaction.coinbase("0xminer", 50.0);
        assertTrue(tx.inputs.isEmpty());
        assertEquals(1, tx.outputs.size());
        assertEquals(50.0, tx.outputs.get(0).amount);
        assertNotNull(tx.txId);
        assertFalse(tx.txId.isEmpty());
    }

    @Test
    void blockHeaderMeetsDifficulty() {
        BlockHeader h = new BlockHeader("0".repeat(64), "merkle", 0.0, 1);
        // nonce=0, difficulty=1 — may or may not pass, just check API
        boolean result = h.meetsDifficulty();
        // hash is deterministic
        assertEquals(result, h.meetsDifficulty());
    }

    @Test
    void genesisBlock() {
        Block g = Block.genesis(4);
        assertEquals("genesis", g.transactions.get(0).txId);
        assertEquals("0".repeat(64), g.header.parentHash);
        assertNotNull(g.hash());
    }

    @Test
    void blockHashDeterministic() {
        Block g1 = Block.genesis(4);
        Block g2 = Block.genesis(4);
        assertEquals(g1.hash(), g2.hash());
    }
}
