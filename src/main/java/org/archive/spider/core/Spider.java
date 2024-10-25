package org.archive.spider.core;

import org.archive.spider.util.PathUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;

public class Spider {

    private final String baseUrl;
    private final Proxy proxy;

    public Spider(@NotNull String baseUrl) {
        this(baseUrl, null);
    }

    public Spider(@NotNull String baseUrl, @Nullable Proxy proxy) {
        this.baseUrl = baseUrl;
        this.proxy = proxy;
    }

    public Node search(@NotNull final String path, @NotNull String xpath) throws IOException {
        final String url = String.format("%s%s", baseUrl, path);
        Document document = Jsoup.connect(url)
                .proxy(proxy)
                .get();
        Elements elements = document.selectXpath(xpath);
        ArrayList<Object> data = new ArrayList<>();
        for (Element element: elements) {
            String href = element.attr("href");
            if (PathUtils.isDirectory(href)) {
                data.add(search(PathUtils.addSeparate(path) + href, xpath));
            } else {
                data.add(new Node(PathUtils.getName(href), baseUrl + PathUtils.addSeparate(path) + href));
            }
        }
        return new Node(Node.TYPE_DIRECTORY, PathUtils.getName(path), data);
    }


}
