package com.taxoin.consensus;

public enum ConsensusStatus {
    PROPOSE,
    PREVOTE,
    PRECOMMIT,
    COMMIT,
    TIMEOUT,
    REJECTED
}
