package com.taxoin.storage;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.taxoin.core.Block;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.merge.ContentMergeStrategy;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Git-backed blockchain storage using JGit.
 *
 * Maps git_backend.py's 17 operations to JGit API.
 * Wire-compatible: block.json format is identical to Python.
 *
 * Key design:
 *  - Block → git commit (block.json in .taxoin/)
 *  - Branch → git branch
 *  - Metadata → git notes (refs/notes/commits)
 *  - Timestamps → PersonIdent with epoch millis
 */
public class JGitBlockchain implements AutoCloseable {

    private static final String CHAIN_DIR  = ".taxoin";
    private static final String BLOCK_FILE = "block.json";
    private static final String NOTES_REF  = "refs/notes/commits";
    private static final String GIT_USER   = "taxoin";
    private static final String GIT_EMAIL  = "taxoin@localhost";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final Path repoPath;
    private final Path chainDir;
    private final Git git;
    private final Repository repo;

    // ── Constructor / init ────────────────────────────────────────────────────

    public JGitBlockchain(Path repoPath) throws IOException, GitAPIException {
        this.repoPath = repoPath.toAbsolutePath();
        this.chainDir = this.repoPath.resolve(CHAIN_DIR);
        Files.createDirectories(chainDir);

        File gitDir = this.repoPath.resolve(".git").toFile();
        if (!gitDir.exists()) {
            this.git = Git.init()
                    .setDirectory(this.repoPath.toFile())
                    .setInitialBranch("main")
                    .call();
            this.repo = this.git.getRepository();
            configureUser();
            // Write genesis block
            Block genesis = Block.genesis(4);
            storeBlock(genesis);
        } else {
            this.git = Git.open(this.repoPath.toFile());
            this.repo = this.git.getRepository();
        }
    }

    private void configureUser() throws IOException {
        StoredConfig cfg = repo.getConfig();
        cfg.setString("user", null, "name",  GIT_USER);
        cfg.setString("user", null, "email", GIT_EMAIL);
        cfg.save();
    }

    // ── Block storage ─────────────────────────────────────────────────────────

    private String storeBlock(Block block) throws IOException, GitAPIException {
        // Write block.json
        Path blockPath = chainDir.resolve(BLOCK_FILE);
        MAPPER.writeValue(blockPath.toFile(), block);

        // Stage
        git.add().addFilepattern(CHAIN_DIR + "/" + BLOCK_FILE).call();

        // Commit with correct timestamp (skip epoch-0 for genesis)
        String msg = buildCommitMessage(block);
        PersonIdent ident = buildIdent(block.header.timestamp);

        git.commit()
                .setAuthor(ident)
                .setCommitter(ident)
                .setAllowEmpty(true)
                .setMessage(msg)
                .call();

        return repo.resolve(Constants.HEAD).getName();
    }

    private PersonIdent buildIdent(double timestamp) {
        if (timestamp > 0) {
            long epochMs = (long)(timestamp * 1000L);
            return new PersonIdent(GIT_USER, GIT_EMAIL, epochMs, 0);
        }
        return new PersonIdent(GIT_USER, GIT_EMAIL);
    }

    private String buildCommitMessage(Block block) {
        return "BLOCK " + block.hash() + "\n"
             + "Parent: " + block.header.parentHash + "\n"
             + "Difficulty: " + block.header.difficulty + "\n"
             + "Nonce: " + block.header.nonce + "\n"
             + "Txs: " + (block.transactions == null ? 0 : block.transactions.size());
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public String addBlock(Block block) throws IOException, GitAPIException {
        return storeBlock(block);
    }

    public Block getLatestBlock() throws IOException {
        Path blockPath = chainDir.resolve(BLOCK_FILE);
        if (!Files.exists(blockPath)) return null;
        return MAPPER.readValue(blockPath.toFile(), Block.class);
    }

    public int getChainHeight() throws IOException {
        ObjectId head = repo.resolve(Constants.HEAD);
        if (head == null) return 0;
        try (RevWalk rw = new RevWalk(repo)) {
            rw.markStart(rw.parseCommit(head));
            int count = 0;
            for (RevCommit ignored : rw) count++;
            return count;
        }
    }

    public Map<String, Object> getBlockByHash(String commitHash) throws IOException {
        ObjectId commitId;
        try {
            commitId = repo.resolve(commitHash);
        } catch (Exception e) {
            return null;
        }
        if (commitId == null) return null;
        try (RevWalk rw = new RevWalk(repo)) {
            RevCommit commit = rw.parseCommit(commitId);
            TreeWalk tw = TreeWalk.forPath(repo, CHAIN_DIR + "/" + BLOCK_FILE, commit.getTree());
            if (tw == null) return null;
            ObjectLoader loader = repo.open(tw.getObjectId(0));
            String json = new String(loader.getBytes(), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, Map.class);
        } catch (org.eclipse.jgit.errors.MissingObjectException e) {
            return null;
        }
    }

    public boolean verifyChain() throws IOException {
        ObjectId head = repo.resolve(Constants.HEAD);
        if (head == null) return true;
        try (RevWalk rw = new RevWalk(repo)) {
            rw.markStart(rw.parseCommit(head));
            for (RevCommit commit : rw) {
                Map<String, Object> block = getBlockByHash(commit.getName());
                if (block == null && commit.getParentCount() > 0) return false;
            }
        }
        return true;
    }

    // ── Branch operations ─────────────────────────────────────────────────────

    public String createBranch(String name, String fromRef) throws GitAPIException, IOException {
        String start = fromRef != null ? fromRef : Constants.HEAD;
        git.branchCreate()
           .setName(name)
           .setStartPoint(start)
           .call();
        return name;
    }

    public void switchBranch(String name) throws GitAPIException {
        git.checkout().setName(name).call();
    }

    public List<String> listBranches() throws GitAPIException {
        List<String> result = new ArrayList<>();
        for (Ref ref : git.branchList().call()) {
            result.add(Repository.shortenRefName(ref.getName()));
        }
        return result;
    }

    public String getCurrentBranch() throws IOException {
        return repo.getBranch();
    }

    public void deleteBranch(String name, boolean force) throws GitAPIException {
        git.branchDelete().setBranchNames(name).setForce(force).call();
    }

    public String getBranchHead(String name) throws IOException {
        ObjectId id = repo.resolve(name);
        if (id == null) throw new IOException("Branch not found: " + name);
        return id.getName();
    }

    /**
     * Merge source into target.
     * strategy: "ours" or "theirs" (maps to ContentMergeStrategy)
     *
     * IMPORTANT: uses MergeStrategy.RECURSIVE + ContentMergeStrategy
     * NOT MergeStrategy.THEIRS (which is -s theirs, replaces whole tree)
     */
    public String mergeBranches(String source, String target, String strategy)
            throws GitAPIException, IOException {
        switchBranch(target);
        ObjectId sourceId = repo.resolve(source);

        ContentMergeStrategy cms = "theirs".equals(strategy)
                ? ContentMergeStrategy.THEIRS
                : ContentMergeStrategy.OURS;

        MergeResult result = git.merge()
                .include(sourceId)
                .setStrategy(MergeStrategy.RECURSIVE)
                .setContentMergeStrategy(cms)
                .setMessage("Merge " + source + " into " + target)
                .call();

        if (!result.getMergeStatus().isSuccessful()) {
            // Abort merge: reset --hard to pre-merge state
            git.reset().setMode(ResetCommand.ResetType.HARD).call();
            throw new RuntimeException("Merge conflict: " + result.getMergeStatus());
        }
        return repo.resolve(Constants.HEAD).getName();
    }

    /** Returns [ahead, behind]: commits in b1 not in b2, and vice versa. */
    public int[] getDivergence(String branch1, String branch2) throws IOException {
        ObjectId b1 = repo.resolve(branch1);
        ObjectId b2 = repo.resolve(branch2);
        if (b1 == null || b2 == null) return new int[]{0, 0};

        int ahead  = countCommits(b1, b2);
        int behind = countCommits(b2, b1);
        return new int[]{ahead, behind};
    }

    private int countCommits(ObjectId from, ObjectId excluded) throws IOException {
        try (RevWalk rw = new RevWalk(repo)) {
            rw.markStart(rw.parseCommit(from));
            rw.markUninteresting(rw.parseCommit(excluded));
            int count = 0;
            for (RevCommit ignored : rw) count++;
            return count;
        }
    }

    // ── Git Notes (branch metadata) ───────────────────────────────────────────

    public void setBranchMetadata(String branchName, Map<String, Object> metadata)
            throws IOException {
        String json = MAPPER.writeValueAsString(metadata);
        ObjectId commitId = repo.resolve(branchName);
        if (commitId == null) throw new IOException("Branch not found: " + branchName);

        try (ObjectInserter inserter = repo.newObjectInserter();
             ObjectReader reader = repo.newObjectReader();
             RevWalk rw = new RevWalk(reader)) {

            NoteMap noteMap = loadNoteMap(reader, rw);
            noteMap.set(commitId, json, inserter);
            commitNoteMap(noteMap, inserter, reader, rw);
            inserter.flush();
        }
    }

    public Map<String, Object> getBranchMetadata(String branchName) throws IOException {
        ObjectId commitId = repo.resolve(branchName);
        if (commitId == null) return Map.of();

        try (ObjectReader reader = repo.newObjectReader();
             RevWalk rw = new RevWalk(reader)) {
            NoteMap noteMap = loadNoteMap(reader, rw);
            Note note = noteMap.getNote(commitId);
            if (note == null) return Map.of();
            String json = new String(reader.open(note.getData()).getBytes(), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, Map.class);
        }
    }

    private NoteMap loadNoteMap(ObjectReader reader, RevWalk rw) throws IOException {
        Ref notesRef = repo.findRef(NOTES_REF);
        if (notesRef == null) return NoteMap.newEmptyMap();
        RevCommit notesCommit = rw.parseCommit(notesRef.getObjectId());
        return NoteMap.read(reader, notesCommit);
    }

    private void commitNoteMap(NoteMap noteMap, ObjectInserter inserter,
                               ObjectReader reader, RevWalk rw) throws IOException {
        PersonIdent ident = new PersonIdent(GIT_USER, GIT_EMAIL);
        CommitBuilder cb = new CommitBuilder();
        cb.setTreeId(noteMap.writeTree(inserter));
        cb.setAuthor(ident);
        cb.setCommitter(ident);
        cb.setMessage("Notes updated");

        Ref notesRef = repo.findRef(NOTES_REF);
        if (notesRef != null) {
            cb.setParentId(notesRef.getObjectId());
        }

        ObjectId newCommit = inserter.insert(cb);
        RefUpdate ru = repo.updateRef(NOTES_REF);
        ru.setNewObjectId(newCommit);
        ru.setForceUpdate(true);
        ru.update(rw);
    }

    // ── AutoCloseable ─────────────────────────────────────────────────────────

    @Override
    public void close() {
        git.close();
        repo.close();
    }
}
