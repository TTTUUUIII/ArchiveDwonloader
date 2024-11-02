package org.archive.spider.download;

import me.tongfei.progressbar.*;
import okhttp3.*;
import org.archive.spider.util.Logger;
import org.archive.spider.util.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.HttpStatusException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadManager {

    private static final int MAX_FILENAME_LENGTH = 30;
    private DownloadEventListener listener;

    private final OkHttpClient client;
    private final AtomicInteger indicator = new AtomicInteger(1);
    private final ExecutorService executor;

    public DownloadManager(int jobs, @NotNull DownloadEventListener listener) {
        this(jobs, listener, null);
    }

    public DownloadManager(int jobs, @NotNull DownloadEventListener listener, @Nullable Proxy proxy) {
        client = new OkHttpClient.Builder()
                .proxy(proxy)
                .build();
        executor = Executors.newFixedThreadPool(jobs);
        this.listener = listener;
    }

    public void download(@NotNull URL url, @NotNull String filepath) {
        download(url, Paths.get(filepath));
    }

    public void download(@NotNull String url, @NotNull String filepath) throws MalformedURLException {
        download(new URL(url), Paths.get(filepath));
    }

    public void download(@NotNull URL url, @NotNull Path filepath) {
        executor.execute(new DownloadTask(indicator.getAndIncrement(), url, filepath));
    }

    public void shutdown() {
        executor.shutdown();
    }

    private ProgressBarBuilder newProgressBarBuilder(int jobId, @NotNull String filename, long max) {
        ProgressBarBuilder builder = new ProgressBarBuilder()
                .setInitialMax(max)
                .setMaxRenderedLength(100)
                .setUnit("MB", 1048576)
                .setTaskName(String.format("%-5d %s", jobId, filename));
        if (SystemUtils.isWindows()) {
            builder.setStyle(new ProgressBarStyleBuilder()
                    .refreshPrompt("\r")
                    .leftBracket(Logger.GREEN + "│")
                    .delimitingSequence("")
                    .rightBracket("│" + Logger.RESET)
                    .block('█')
                    .space(' ')
                    .fractionSymbols(" ▏▎▍▌▋▊▉")
                    .rightSideFractionSymbol(' ')
                    .build());
        } else {
            builder.setStyle(new ProgressBarStyleBuilder()
                    .refreshPrompt("\r")
                    .leftBracket(Logger.GREEN)
                    .delimitingSequence("\u001b[90m")
                    .rightBracket(Logger.RESET)
                    .block('━')
                    .space('━')
                    .fractionSymbols(" ╸")
                    .rightSideFractionSymbol('╺')
                    .build());
        }
        return builder;
    }

    private class DownloadTask implements Runnable {

        private final int jobId;
        private final Path filepath;
        private final URL url;

        DownloadTask(int jobId, @NotNull URL url, @NotNull Path filepath) {
            this.jobId = jobId;
            this.filepath = filepath;
            this.url = url;
        }

        @Override
        public void run() {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            final String filename = filepath.getFileName().toString();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body != null) {
                        String croppedFilename = filepath.getFileName().toString();
                        if (filename.length() > MAX_FILENAME_LENGTH) {
                            croppedFilename = filename.substring(0, MAX_FILENAME_LENGTH - 3) + "...";
                        } else {
                            croppedFilename = String.format("%-30s", croppedFilename);
                        }
                        try (InputStream inputStream = ProgressBar.wrap(body.byteStream(), newProgressBarBuilder(jobId, croppedFilename, body.contentLength()));
                             OutputStream out = Files.newOutputStream(filepath)) {
                            int readNumInBytes;
                            byte[] buffer = new byte[1024];
                            while ((readNumInBytes = inputStream.read(buffer)) != -1) {
                                if (readNumInBytes > 0) {
                                    out.write(buffer, 0, readNumInBytes);
                                }
                            }
                        }
                        listener.onDownloadSuccessful(jobId, filename, url);
                    } else {
                        throw new IOException("Remote server has no response content.");
                    }
                } else {
                    Logger.error("Failed to request file! " + response.code());
                    throw new HttpStatusException("Failed to request file!", response.code(), url.toString());
                }
            } catch (IOException e) {
                listener.onDownloadFailure(jobId, filename, url, e);
                Logger.error(e.getLocalizedMessage());
                if (filepath.toFile().exists()) {
                    try {
                        Files.delete(filepath);
                    } catch (IOException ex) {
                        Logger.error("Failed to delete \"" + filepath + "\"" + ex);
                    }
                }
            }
            listener.onJobCompleted(jobId);
        }
    }

    public interface DownloadEventListener {
        void onDownloadSuccessful(int jobId, @Nullable String filename, @Nullable URL url);

        void onDownloadFailure(int jobId, @Nullable String filename, @Nullable URL url, Exception e);

        void onJobCompleted(int jobId);
    }
}
