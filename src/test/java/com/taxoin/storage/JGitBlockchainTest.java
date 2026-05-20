package com.taxoin.storage;

import com.taxoin.core.Block;
import com.taxoin.core.BlockHeader;
import com.taxoin.core.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JGitBlockchainTest {

    @TempDir Path tmp;
    JGitBlockchain chain;

    @BeforeEach
    void setup() throws Exception {
        chain = new JGitBlockchain(tmp);
    }

    @AfterEach
    void teardown() {
        if (chain != null) chain.close();
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Test
    void initCreatesGitRepo() {
        assertTrue(tmp.resolve(".git").toFile().exists());
    }

    @Test
    void initCreatesGenesisBlock() throws Exception {
        Block latest = chain.getLatestBlock();
        assertNotNull(latest);
        assertEquals("genesis", latest.transactions.get(0).txId);
    }

    @Test
    void chainHeightAfterInitIsOne() throws Exception {
        assertEquals(1, chain.getChainHeight());
    }

    // ── Add block ─────────────────────────────────────────────────────────────

    @Test
    void addBlockIncreasesHeight() throws Exception {
        Block b = makeBlock();
        chain.addBlock(b);
        assertEquals(2, chain.getChainHeight());
    }

    @Test
    void addBlockReturnsCommitSha() throws Exception {
        String sha = chain.addBlock(makeBlock());
        assertNotNull(sha);
        assertEquals(40, sha.length());
        assertTrue(sha.matches("[0-9a-f]+"));
    }

    @Test
    void addMultipleBlocks() throws Exception {
        chain.addBlock(makeBlock());
        chain.addBlock(makeBlock());
        chain.addBlock(makeBlock());
        assertEquals(4, chain.getChainHeight()); // genesis + 3
    }

    // ── Get block by hash ─────────────────────────────────────────────────────

    @Test
    void getBlockByHashReturnsData() throws Exception {
        String sha = chain.addBlock(makeBlock());
        Map<String, Object> data = chain.getBlockByHash(sha);
        assertNotNull(data);
        assertTrue(data.containsKey("header"));
        assertTrue(data.containsKey("transactions"));
    }

    @Test
    void getBlockByHashUnknownReturnsNull() throws Exception {
        assertNull(chain.getBlockByHash("0".repeat(40)));
    }

    // ── Verify chain ──────────────────────────────────────────────────────────

    @Test
    void verifyChainPassesOnCleanChain() throws Exception {
        chain.addBlock(makeBlock());
        chain.addBlock(makeBlock());
        assertTrue(chain.verifyChain());
    }

    // ── Branch operations ─────────────────────────────────────────────────────

    @Test
    void createBranch() throws Exception {
        chain.createBranch("feature/test", "main");
        assertTrue(chain.listBranches().contains("feature/test"));
    }

    @Test
    void listBranchesContainsMain() throws Exception {
        List<String> branches = chain.listBranches();
        assertTrue(branches.contains("main"));
    }

    @Test
    void switchBranch() throws Exception {
        chain.createBranch("dev", "main");
        chain.switchBranch("dev");
        assertEquals("dev", chain.getCurrentBranch());
    }

    @Test
    void deleteBranch() throws Exception {
        chain.createBranch("temp", "main");
        chain.switchBranch("main");
        chain.deleteBranch("temp", true);
        assertFalse(chain.listBranches().contains("temp"));
    }

    @Test
    void getBranchHead() throws Exception {
        String head = chain.getBranchHead("main");
        assertNotNull(head);
        assertEquals(40, head.length());
    }

    // ── Merge ─────────────────────────────────────────────────────────────────

    @Test
    void mergeBranches() throws Exception {
        // Create feature branch and add a block
        chain.createBranch("feature", "main");
        chain.switchBranch("feature");
        chain.addBlock(makeBlock());

        // Merge back into main
        String mergeCommit = chain.mergeBranches("feature", "main", "theirs");
        assertNotNull(mergeCommit);
        assertEquals("main", chain.getCurrentBranch());
        assertTrue(chain.getChainHeight() >= 2);
    }

    // ── Divergence ────────────────────────────────────────────────────────────

    @Test
    void divergenceOnSameBranchIsZero() throws Exception {
        int[] div = chain.getDivergence("main", "main");
        assertEquals(0, div[0]);
        assertEquals(0, div[1]);
    }

    @Test
    void divergenceAfterBranchCommit() throws Exception {
        chain.createBranch("dev", "main");
        chain.switchBranch("dev");
        chain.addBlock(makeBlock());
        chain.switchBranch("main");

        int[] div = chain.getDivergence("dev", "main");
        assertEquals(1, div[0]); // dev is 1 ahead
        assertEquals(0, div[1]); // main is 0 ahead
    }

    // ── Git Notes (metadata) ──────────────────────────────────────────────────

    @Test
    void setBranchMetadataAndGet() throws Exception {
        Map<String, Object> meta = Map.of("owner", "0xalice", "created_at", 1234567890L);
        chain.setBranchMetadata("main", meta);
        Map<String, Object> loaded = chain.getBranchMetadata("main");
        assertEquals("0xalice", loaded.get("owner"));
    }

    @Test
    void getMetadataReturnsEmptyMapWhenNone() throws Exception {
        Map<String, Object> meta = chain.getBranchMetadata("main");
        assertNotNull(meta);
        assertTrue(meta.isEmpty());
    }

    @Test
    void metadataOverwrite() throws Exception {
        chain.setBranchMetadata("main", Map.of("v", "first"));
        chain.setBranchMetadata("main", Map.of("v", "second"));
        assertEquals("second", chain.getBranchMetadata("main").get("v"));
    }

    // ── Reopen existing repo ──────────────────────────────────────────────────

    @Test
    void reopenExistingRepo() throws Exception {
        chain.addBlock(makeBlock());
        int height = chain.getChainHeight();
        chain.close();

        try (JGitBlockchain reopened = new JGitBlockchain(tmp)) {
            assertEquals(height, reopened.getChainHeight());
        }
        chain = null; // avoid double-close in teardown
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static Block makeBlock() {
        BlockHeader h = new BlockHeader("0".repeat(64), "merkle",
                System.currentTimeMillis() / 1000.0, 1);
        return new Block(h, List.of(), Map.of());
    }
}
