package org.archive.spider.util;

import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathUtils {
    private PathUtils() {}

    public static boolean isDirectory(@NotNull String path) {
        return path.endsWith("/");
    }

    public static String getName(@NotNull String path) {
        if (isDirectory(path)) {
            path = path.substring(0, path.length() - 1);
        }
        final String[] tmp = path.split("/");
        return tmp[tmp.length - 1];
    }

    public static String getExtensionName(@NotNull String path) {
        int index = path.lastIndexOf(".");
        if (index != -1) {
            return path.substring(index);
        }
        return "";
    }

    public static String addSeparate(@NotNull String path) {
        if (path.endsWith("/")) {
            return path;
        }
        return path + "/";
    }

    public static Path decode(Path s) {
        return Paths.get(decode(s.toString()));
    }

    public static String decode(String s) {
        try {
            return URLDecoder.decode(s, "utf8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace(System.err);
        }
        return s;
    }
}
