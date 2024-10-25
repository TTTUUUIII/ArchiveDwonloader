package org.archive.spider;

import org.archive.spider.core.Node;
import org.archive.spider.core.Spider;
import org.archive.spider.download.DownloadManager;
import org.archive.spider.util.Logger;
import org.archive.spider.util.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static String[] focus;
    private static String out = System.getProperty("user.dir");
    private static Proxy proxy = Proxy.NO_PROXY;
    private static final Set<String> resources = new LinkedHashSet<>();
    private final static String XPATH = "//*[@id=\"maincontent\"]/div/div/pre/table/tbody/tr[position()>1]/td/a[1]";

    private static class Kernel {
        private final Spider spider = new Spider("https://archive.org/download/", proxy);
        private final DownloadManager dm = new DownloadManager(proxy);

        public void run() {
            for (String resource : resources) {
                Logger.message("Searching in \"" + resource + "\" ...");
                Node node = onSearchNode(resource, XPATH);
                if (node != null) {
                    int[] statistics = onStatistics(node);
                    System.out.printf("Total: %-5d\tFocus: %s%-5d%s\n\n", statistics[0], Logger.GREEN, statistics[1], Logger.RESET);
                    dm.resetIndex();
                    onRecursiveNode(node, null);
                } else {
                    Logger.waring("Sorry, no resources found in \"" + resource + "\"");
                }
            }
        }

        private @Nullable Node onSearchNode(String path, String xpath) {
            try {
                return spider.search(path, xpath);
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
            return null;
        }

        private void onRecursiveNode(@NotNull Node node, @Nullable String basePath) {
            if (basePath == null) basePath = "";
            Path directoryPath = PathUtils.decode(Paths.get(out, basePath, node.name));
            if (!directoryPath.toFile().exists() && !directoryPath.toFile().mkdir()) {
                Logger.error("Unable create directory in \"%s" + directoryPath + "\"");
                return;
            }
            if (node.data instanceof List<?>) {
                for (Node it : (List<Node>) node.data) {
                    if (it.type == Node.TYPE_DIRECTORY) {
                        onRecursiveNode(it, node.name);
                    } else {
                        onDownload(it, directoryPath);
                    }
                }
            }
        }

        private void onDownload(Node node, Path parent) {
            if (node.type != Node.TYPE_FILE || !isFocus(node)) return;
            Path filePath = PathUtils.decode(parent.resolve(node.name));
            try {
                URL url = new URL((String) node.data);
                if (!filePath.toFile().exists()) {
                    dm.download(url, filePath);
                } else {
                    Logger.waring(filePath + " already exists, skip.");
                }
            } catch (IOException e) {
                Logger.waring("Failed download \"" + filePath + "\"\n" + e);
            }
        }

        private int[/*total, focus*/] onStatistics(Node node) {
            int[] statistics = new int[]{0, 0};
            if (node.type == Node.TYPE_FILE) {
                return isFocus(node) ? new int[]{1, 1} : new int[] {1, 0};
            } else {
                for (Node it: (List<Node>) node.data) {
                    int[] tmp = onStatistics(it);
                    statistics[0] += tmp[0];
                    statistics[1] += tmp[1];
                }
            }
            return statistics;
        }

        private boolean isFocus(@NotNull Node node) {
            if (focus == null || focus.length == 0) return true;
            String ext = PathUtils.getExtensionName(PathUtils.decode(node.name));
            if (!ext.isEmpty()) {
                ext = ext.substring(1);
            }
            for (String it: focus) {
                if (it.toLowerCase(Locale.ROOT).equals(ext.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    public static void main(String[] args) {
        parseArgs(args);
        if (checkArgs()) {
            final Kernel kernel = new Kernel();
            kernel.run();
        }
    }

    private static boolean checkArgs() {
        if (resources.isEmpty()) {
            Logger.waring("No resource specified, do nothing.");
            return false;
        }
        Path outPath = Paths.get(out);
        if (!outPath.toFile().exists()) {
            Logger.error("Failed! Out directory not exists! \"" + outPath.toAbsolutePath() + "\"");
            return false;
        }
        return true;
    }

    private static void parseArgs(String[] args) {
        int index = 0;
        while (index < args.length) {
            final String[] option = args[index].split("=");
            switch (option[0]) {
                case "--add-resource":
                    resources.add(PathUtils.addSeparate(option[1]));
                    break;
                case "--list-resource":
                    try {
                        List<String> list = Files.readAllLines(Paths.get(option[1]));
                        for (String item : list) {
                            if (item.isEmpty()) {
                                continue;
                            }
                            resources.add(PathUtils.addSeparate(item));
                        }
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                    }
                    break;
                case "--out":
                    out = option[1];
                    break;
                case "--focus":
                    focus = option[1].split(",");
                    break;
                case "--http-proxy":
                    proxy = parseProxy(option[1]);
                default:
            }
            index++;
        }
    }

    private static Proxy parseProxy(String str) {
        try {
            URL url = new URL(str);
            return new Proxy(Proxy.Type.valueOf(url.getProtocol().toUpperCase(Locale.ROOT)), new InetSocketAddress(url.getHost(), url.getPort()));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}