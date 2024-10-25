package org.archive.spider.util;

import org.jetbrains.annotations.NotNull;

public final class Logger {
    private Logger() {}

    public static final String RESET = "\u001B[0m";
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    public static void error(@NotNull String msg) {
        System.err.println(RED + msg + RESET);
    }

    public static void info(@NotNull String msg) {
        System.out.println(CYAN + msg + RESET);
    }

    public static void waring(@NotNull String msg) {
        System.out.println(YELLOW + msg + RESET);
    }

    public static void success(@NotNull String msg) {
        System.out.println(GREEN + msg + RESET);
    }

    public static void message(@NotNull String msg) {
        System.out.println(msg);
    }
}
