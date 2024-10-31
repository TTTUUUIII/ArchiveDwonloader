package org.archive.spider;

import org.archive.spider.core.Node;
import org.archive.spider.core.Spider;
import org.archive.spider.download.DownloadManager;
import org.archive.spider.util.Logger;
import org.archive.spider.util.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    private static String[] focus;
    private static String out = System.getProperty("user.dir");
    private static Proxy proxy = Proxy.NO_PROXY;
    private static final Set<String> resources = new LinkedHashSet<>();
    private final static String XPATH = "//*[@id=\"maincontent\"]/div/div/pre/table/tbody/tr[position()>1]/td/a[1]";
    private static int jobs = 1;

    private static class Kernel implements DownloadManager.DownloadEventListener {
        private final Spider spider = new Spider("https://archive.org/download/", proxy);
        private final DownloadManager dm = new DownloadManager(jobs, this, proxy);
        private final AtomicInteger total = new AtomicInteger(0);
        private final AtomicInteger success = new AtomicInteger(0);
        private final AtomicInteger failure = new AtomicInteger(0);
        private int skip = 0;

        public void run() {
            ArrayList<Node> nodes = new ArrayList<>();
            for (String resource : resources) {
                Logger.message("Searching in \"" + resource + "\" ...");
                Node node = onSearchNode(resource, XPATH);
                if (node != null) {
                    int[] statistics = statistics(node);
                    System.out.printf("Total: %-5d\tFocus: %s%-5d%s\n", statistics[0], Logger.GREEN, statistics[1], Logger.RESET);
                    total.addAndGet(statistics[1]);
                    nodes.add(node);
                } else {
                    Logger.waring("Sorry, no resources found in \"" + resource + "\"");
                }
            }
            System.out.println();
            nodes.forEach(this::onRecursiveNode);
        }

        private @Nullable Node onSearchNode(String path, String xpath) {
            try {
                return spider.search(path, xpath);
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }
            return null;
        }

        private void onRecursiveNode(@NotNull Node node) {
            onRecursiveNode(node, null);
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
                    skip++;
                    Logger.waring(filePath + " already exists, skip.");
                }
            } catch (MalformedURLException e) {
                e.printStackTrace(System.err);
                failure.incrementAndGet();
            }
        }

        private void onStatistics() {
            dm.shutdown();
            Logger.message(String.format("\nTotal: %s%d%s, Skip: %s%d%s, Success: %s%d%s, Failure: %s%d%s", Logger.CYAN, total.get(), Logger.RESET, Logger.YELLOW, skip, Logger.RESET, Logger.GREEN, success.get(), Logger.RESET, Logger.RED, failure.get(), Logger.RESET));
        }

        private int[] statistics(Node node) {
            int[] statistics = new int[]{0, 0};
            if (node.type == Node.TYPE_FILE) {
                return isFocus(node) ? new int[]{1, 1} : new int[] {1, 0};
            } else {
                for (Node it: (List<Node>) node.data) {
                    int[] tmp = statistics(it);
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

        @Override
        public void onDownloadFailure(int jobId, @Nullable String filename, @Nullable URL url, Exception e) {
            failure.incrementAndGet();
        }

        @Override
        public void onDownloadSuccessful(int jobId, @Nullable String filename, @Nullable URL url) {
            success.incrementAndGet();
        }

        @Override
        public void onJobCompleted(int jobId) {
            if (success.get() + failure.get() + skip == total.get()) {
                onStatistics();
            }
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
        File outputDirectory = Paths.get(out).toFile();
        if (!outputDirectory.exists() && !outputDirectory.mkdir()) {
            Logger.error("Unable create output directory in \"" + outputDirectory.getAbsolutePath() + "\"");
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
                case "--checklist":
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
                    break;
                case "--jobs":
                    jobs = Integer.parseInt(option[1]);
                    break;
                default:
                    throw new RuntimeException("Unknown option \"" + option[0] + "\"");
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