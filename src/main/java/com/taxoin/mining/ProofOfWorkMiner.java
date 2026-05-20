package com.taxoin.mining;

import com.taxoin.core.BlockHeader;

public final class ProofOfWorkMiner {

    public static final int MAX_NONCE = 2_000_000;

    private ProofOfWorkMiner() {}

    public static BlockHeader mine(BlockHeader header) {
        return mine(header, MAX_NONCE);
    }

    public static BlockHeader mine(BlockHeader header, int maxNonce) {
        if (header.meetsDifficulty()) return header;
        for (int nonce = 1; nonce <= maxNonce; nonce++) {
            header.nonce = nonce;
            if (header.meetsDifficulty()) return header;
        }
        throw new RuntimeException(
                "Failed to mine block: no valid nonce in " + maxNonce + " attempts "
                + "(difficulty=" + header.difficulty + ")");
    }

    public static boolean validatePoW(BlockHeader header) {
        return header.meetsDifficulty();
    }
}
