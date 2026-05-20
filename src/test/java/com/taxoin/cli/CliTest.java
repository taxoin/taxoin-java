package com.taxoin.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CLI tests via Picocli's programmatic CommandLine API.
 * No process spawning — calls commands in-process.
 */
class CliTest {

    @TempDir Path tmp;
    CommandLine cmd;
    StringWriter out;
    StringWriter err;

    @BeforeEach
    void setup() {
        TaxoinCli cli = new TaxoinCli();
        cmd = new CommandLine(cli)
                .setCaseInsensitiveEnumValuesAllowed(true);
        out = new StringWriter();
        err = new StringWriter();
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
    }

    int run(String... args) {
        // Prepend --dir to all commands so they use the temp dir
        String[] fullArgs = new String[args.length + 2];
        fullArgs[0] = "--dir";
        fullArgs[1] = tmp.toString();
        System.arraycopy(args, 0, fullArgs, 2, args.length);
        out.getBuffer().setLength(0);
        err.getBuffer().setLength(0);
        return cmd.execute(fullArgs);
    }

    // ── Root ──────────────────────────────────────────────────────────────────

    @Test
    void root_shows_help() {
        int code = run();
        assertEquals(0, code);
        // Usage is shown
        assertTrue(out.toString().contains("taxoin") || out.toString().contains("Usage"));
    }

    @Test
    void help_flag_works() {
        int code = cmd.execute("--help");
        assertEquals(0, code);
    }

    // ── init ──────────────────────────────────────────────────────────────────

    @Test
    void init_succeeds() {
        int code = run("init");
        assertEquals(0, code);
        assertTrue(out.toString().contains("Chain initialized"));
    }

    // ── status ────────────────────────────────────────────────────────────────

    @Test
    void status_succeeds() {
        int code = run("status");
        assertEquals(0, code);
        assertTrue(out.toString().contains("Branches"));
    }

    // ── accounts ─────────────────────────────────────────────────────────────

    @Test
    void accounts_no_accounts_message() {
        int code = run("accounts");
        assertEquals(0, code);
        assertTrue(out.toString().contains("No accounts") || out.toString().contains("Address"));
    }

    // ── wallet new ────────────────────────────────────────────────────────────

    @Test
    void wallet_new_creates_wallet() {
        int code = run("wallet", "new");
        assertEquals(0, code);
        assertTrue(out.toString().contains("✓ New wallet created!"));
        assertTrue(out.toString().contains("0x"));
    }

    @Test
    void wallet_new_different_each_time() {
        run("wallet", "new");
        String addr1 = extractAddress(out.toString());
        out.getBuffer().setLength(0);
        run("wallet", "new");
        String addr2 = extractAddress(out.toString());
        assertNotEquals(addr1, addr2);
    }

    @Test
    void wallet_address_shows_address_after_create() {
        run("wallet", "new");
        String created = extractAddress(out.toString());

        out.getBuffer().setLength(0);
        run("wallet", "address");
        assertTrue(out.toString().contains(created));
    }

    @Test
    void wallet_address_no_wallet_message() {
        int code = run("wallet", "address");
        assertEquals(0, code);
        assertTrue(out.toString().contains("No wallet found"));
    }

    // ── balance ───────────────────────────────────────────────────────────────

    @Test
    void balance_unknown_address() {
        int code = run("balance", "0xunknown");
        assertEquals(0, code);
        assertTrue(out.toString().contains("Balance"));
        assertTrue(out.toString().contains("0.0"));
    }

    // ── genesis ───────────────────────────────────────────────────────────────

    @Test
    void genesis_attest_prints_message() {
        int code = run("genesis", "attest", "0xnewbie");
        assertEquals(0, code);
        assertTrue(out.toString().contains("attestation") || out.toString().contains("quorum"));
    }

    // ── service ───────────────────────────────────────────────────────────────

    @Test
    void service_list_empty() {
        int code = run("service", "list");
        assertEquals(0, code);
        assertTrue(out.toString().contains("No services") || out.toString().isEmpty()
                || out.toString().contains("provider"));
    }

    @Test
    void service_register_requires_wallet() {
        // No wallet → should print error
        int code = run("service", "register",
                "--type", "sms", "--price", "0.1",
                "--endpoint", "http://test.com", "--description", "Test");
        assertEquals(1, code);
        assertTrue(out.toString().contains("No wallet"));
    }

    @Test
    void service_register_with_wallet() {
        run("wallet", "new");
        int code = run("service", "register",
                "--type", "sms", "--price", "0.1",
                "--endpoint", "http://test.com", "--description", "Test SMS");
        assertEquals(0, code);
        assertTrue(out.toString().contains("✓ Service registered"));
    }

    // ── tx ────────────────────────────────────────────────────────────────────

    @Test
    void tx_send_requires_wallet() {
        int code = run("tx", "send", "0xprovider",
                "--amount", "1.0", "--ref", "sms:0xprovider");
        assertEquals(1, code);
        assertTrue(out.toString().contains("No wallet"));
    }

    @Test
    void tx_send_with_wallet_creates_tx() {
        run("wallet", "new");
        int code = run("tx", "send", "0xprovider",
                "--amount", "0.1", "--ref", "sms:0xprovider");
        assertEquals(0, code);
        assertTrue(out.toString().contains("✓ Transaction created"));
        assertTrue(out.toString().contains("Consumer sig"));
    }

    @Test
    void tx_attest_with_wallet() {
        run("wallet", "new");
        int code = run("tx", "attest", "abc123def456");
        assertEquals(0, code);
        assertTrue(out.toString().contains("✓ Transaction"));
        assertTrue(out.toString().contains("attested"));
    }

    // ── branch ────────────────────────────────────────────────────────────────

    @Test
    void branch_list_shows_main() {
        int code = run("branch", "list");
        assertEquals(0, code);
        // Either "No branches" or the list with main/master
        assertTrue(out.toString().contains("main")
                || out.toString().contains("master")
                || out.toString().contains("No branches"));
    }

    @Test
    void branch_create() {
        int code = run("branch", "create", "0xalice");
        assertEquals(0, code);
        assertTrue(out.toString().contains("✓ Branch created"));
        assertTrue(out.toString().contains("0xalice"));
    }

    @Test
    void branch_status_unknown() {
        int code = run("branch", "status", "nonexistent");
        assertEquals(1, code);
        assertTrue(out.toString().contains("not found"));
    }

    @Test
    void branch_status_main() {
        int code = run("branch", "status", "main");
        assertEquals(0, code);
        assertTrue(out.toString().contains("Branch"));
    }

    // ── validator ─────────────────────────────────────────────────────────────

    @Test
    void validator_list_empty_before_init() {
        int code = run("validator", "list");
        assertEquals(0, code);
        assertTrue(out.toString().contains("No validators"));
    }

    @Test
    void validator_init_creates_validators() {
        int code = run("validator", "init", "--count", "3");
        assertEquals(0, code);
        assertTrue(out.toString().contains("3 validators"));
    }

    // ── chain ─────────────────────────────────────────────────────────────────

    @Test
    void chain_shows_branches() {
        int code = run("chain");
        assertEquals(0, code);
        assertTrue(out.toString().contains("Height") || out.toString().contains("Branch"));
    }

    // ── verify ────────────────────────────────────────────────────────────────

    @Test
    void verify_passes() {
        int code = run("verify");
        assertEquals(0, code);
        assertTrue(out.toString().contains("verified"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String extractAddress(String output) {
        for (String line : output.split("\n")) {
            if (line.contains("0x") && line.contains("Address")) {
                int idx = line.indexOf("0x");
                return line.substring(idx, Math.min(idx + 42, line.length())).trim();
            }
        }
        return "";
    }
}
