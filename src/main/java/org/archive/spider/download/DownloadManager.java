package org.archive.spider.download;

import okhttp3.*;
import org.archive.spider.util.ByteFormatter;
import org.archive.spider.util.ColoredText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class DownloadManager {

    private static final int MAX_FILENAME_LENGTH = 50;

    private Proxy proxy;
    private final OkHttpClient client;
    private int index = 1;
    public DownloadManager() {
        this(null);
    }
    public DownloadManager(@Nullable Proxy proxy) {
        this.proxy = proxy;
        client = new OkHttpClient.Builder()
                .proxy(proxy)
                .build();
    }

    public void download(@NotNull URL url, @NotNull String filepath) throws IOException {
        download(url, Paths.get(filepath));
    }

    public void download(@NotNull String url, @NotNull String filepath) throws IOException {
        download(new URL(url), Paths.get(filepath));
    }

    public void resetIndex() {
        index = 1;
    }

    public void download(@NotNull URL url, @NotNull Path filepath) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try (Response response = client.newCall(request).execute()){
            if (response.isSuccessful()) {
                ResponseBody body = response.body();
                if (body != null) {
                    try(InputStream inputStream = body.byteStream();
                        OutputStream out = Files.newOutputStream(filepath)) {
                        int readNumInBytes;
                        long read = 0;
                        long total = body.contentLength();
                        byte[] buffer = new byte[1024];
                        while ((readNumInBytes = inputStream.read(buffer)) != -1) {
                            if (readNumInBytes > 0) {
                                out.write(buffer, 0, readNumInBytes);
                                read += readNumInBytes;
                                String filename = filepath.getFileName().toString();
                                String progress = "???";
                                String size = "???";
                                if (filename.length() > MAX_FILENAME_LENGTH) {
                                    filename = filename.substring(0, MAX_FILENAME_LENGTH) + "...";
                                }
                                if (total > 0) {
                                    progress = String.format(Locale.US, "%.2f", (float) read / total * 100);
                                    size = ByteFormatter.format(total);
                                }
                                System.out.printf("\r %-5d %-50s %-10s %s%10s%%%s", index, filename, size, ColoredText.CYAN, progress, ColoredText.RESET);
                            }
                        }
                        System.out.println();
                    }
                }
            } else {
                throw new IOException("Failed to request file! " + response.code());
            }
        }
        index++;
    }
}
