package com.taxoin.validator;

public class MergeProposal {
    public String branchName;
    public String proposer;
    public String parentHash;
    public String finalStateHash;
    public int transactionCount;
    public double timestamp;
    public String signature;

    public MergeProposal() {}

    public MergeProposal(String branchName, String proposer, String parentHash,
                         String finalStateHash, int transactionCount,
                         double timestamp, String signature) {
        this.branchName = branchName;
        this.proposer = proposer;
        this.parentHash = parentHash;
        this.finalStateHash = finalStateHash;
        this.transactionCount = transactionCount;
        this.timestamp = timestamp;
        this.signature = signature != null ? signature : "";
    }
}
