package com.taxoin.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Read/write .taxoin/wallet.json — same format as Python. */
public final class WalletManager {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private WalletManager() {}

    public static Map<String, String> load(String taxoinDir) throws IOException {
        Path path = Path.of(taxoinDir, "wallet.json");
        if (!Files.exists(path)) return null;
        return MAPPER.readValue(path.toFile(), Map.class);
    }

    public static void save(String taxoinDir, Map<String, String> wallet) throws IOException {
        Path dir  = Path.of(taxoinDir);
        Files.createDirectories(dir);
        MAPPER.writeValue(dir.resolve("wallet.json").toFile(), wallet);
    }
}
