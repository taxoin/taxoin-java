package com.taxoin.consensus;

import com.taxoin.branch.ConflictDetector;
import com.taxoin.validator.MergeProposal;

import java.util.LinkedHashMap;
import java.util.Map;

public class ConsensusRound {
    public final MergeProposal proposal;
    public final int roundId;
    public ConsensusStatus status;
    public final Map<String, String> prevotes   = new LinkedHashMap<>();
    public final Map<String, String> precommits = new LinkedHashMap<>();
    public ConflictDetector.MergeResult result;
    public final double startedAt;

    public ConsensusRound(MergeProposal proposal, int roundId,
                          ConsensusStatus status, double startedAt) {
        this.proposal  = proposal;
        this.roundId   = roundId;
        this.status    = status;
        this.startedAt = startedAt;
    }
}
