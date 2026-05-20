package com.taxoin.cli;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exit = new CommandLine(new TaxoinCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exit);
    }
}
