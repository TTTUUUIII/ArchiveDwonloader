package org.archive.spider.util;

import java.util.Locale;

public final class SystemUtils {
    private SystemUtils() {}

    public static final String OS_NAME = System.getProperty("os.name").toLowerCase(Locale.ROOT);

    public static final String OS_VERSION = System.getProperty("os.version");

    public static boolean isWindows() {
        return OS_NAME.contains("windows");
    }
}
