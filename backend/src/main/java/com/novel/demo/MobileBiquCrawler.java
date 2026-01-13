package com.novel.demo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MobileBiquCrawler {

    private static final String LIST_URL = "http://m.biquxs.info/199/199750/";
    private static final String BASE_URL = "http://m.biquxs.info";
    private static final Pattern CHAPTER_LINK_PATTERN = Pattern.compile(
            "<p>\\s*<a\\s+href=\"([^\"]+)\">\\s*(.*?)\\s*</a>\\s*</p>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NEXT_LINK_PATTERN = Pattern.compile(
            "<a[^>]+href=\"([^\"]+)\"[^>]*>\\s*(下一[页章])\\s*</a>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_ENTITY_PATTERN = Pattern.compile("&#(x?[0-9a-fA-F]+);", Pattern.CASE_INSENSITIVE);
    private static final int REQUEST_PAUSE_MS = 400;

    private final HttpClient client;

    public MobileBiquCrawler() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public static void main(String[] args) throws Exception {
        testVideoGeneration();
    }
 private static void testVideoGeneration() throws Exception {

        System.out.println("=== 视频生成 API 测试 ===\n");



        String url = BASE_URL + "/v2/videos/generations";



        // 构建请求体 - 使用首图生成视频

        String requestBody = """

            {

                "model": "veo3.1",

                "prompt": "镜头缓缓推进，小猫在花园里追逐蝴蝶，阳光明媚，花瓣飘落",

                "images": ["https://szridea.oss-cn-beijing.aliyuncs.com/sora-anime/generated-images/b0eba0d3-7b4b-4619-ba6b-43e8c7e4ff3f.jpeg"],

                "aspect_ratio": "16:9",

                "hd": false,

                "duration": "5",

                "watermark": true,

                "private": true

            }

            """;

        System.out.println("请求 URL: " + url);

        System.out.println("请求体: " + requestBody);

        System.out.println("\n发送请求中...\n");

        HttpClient client = HttpClient.newBuilder()

                .connectTimeout(Duration.ofSeconds(30))

                .build();

        HttpRequest request = HttpRequest.newBuilder()

                .uri(URI.create(url))

                .header("Content-Type", "application/json")

                .header("Authorization", "Bearer " + "sk-jUSPTmh5C2PbGHb98cFcA72eFcA54b4aB2Cf21E33b9cA049")

                .POST(HttpRequest.BodyPublishers.ofString(requestBody))

                .timeout(Duration.ofSeconds(120))

                .build();

        long startTime = System.currentTimeMillis();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.currentTimeMillis();

        System.out.println("响应状态码: " + response.statusCode());

        System.out.println("耗时: " + (endTime - startTime) + "ms");

        System.out.println("响应体:\n" + response.body());



        // 如果成功，提示如何查询状态

        if (response.statusCode() == 200) {

            System.out.println("\n提示: 使用以下命令查询视频生成状态:");

            System.out.println("java ImageApiTest video-status <taskId>");

        }

    }


    private void run(Arguments arguments) throws IOException, InterruptedException {
        Files.createDirectories(arguments.outputDir);

        System.out.println("开始获取章节目录: " + LIST_URL);
        String listHtml = fetch(LIST_URL);
        String chapterListBlock = extractDivById(listHtml, "chapterlist")
                .orElseThrow(() -> new IllegalStateException("未找到章节列表"));

        List<Chapter> chapters = parseChapterList(chapterListBlock);
        if (chapters.isEmpty()) {
            System.out.println("未能解析到任何章节链接");
            return;
        }

        int total = chapters.size();
        int padding = Math.max(3, Integer.toString(total).length());
        int from = Math.max(1, arguments.fromIndex);
        int to = Math.min(arguments.toIndex.orElse(total), total);
        if (from > to) {
            System.out.printf(Locale.ROOT, "范围无效: 起始 %d 大于 结束 %d%n", from, to);
            return;
        }

        System.out.printf(Locale.ROOT, "总章节数: %d, 本次下载范围: %d-%d%n", total, from, to);

        for (int i = from; i <= to; i++) {
            Chapter chapter = chapters.get(i - 1);
            String safeTitle = sanitizeFileName(chapter.title);
            Path targetFile = arguments.outputDir.resolve(String.format(Locale.ROOT, "%0" + padding + "d %s.txt", i, safeTitle));

            System.out.printf(Locale.ROOT, "[%d/%d] 下载 %s -> %s%n", i, total, chapter.title, targetFile.getFileName());
            try {
                String content = fetchFullChapter(chapter.url);
                Files.writeString(targetFile, content, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                System.err.printf(Locale.ROOT, "章节 %s 下载失败: %s%n", chapter.title, ex.getMessage());
            }

            Thread.sleep(REQUEST_PAUSE_MS);
        }

        System.out.println("下载完成，文件保存在: " + arguments.outputDir.toAbsolutePath());
    }

    private String fetchFullChapter(String relativeUrl) throws IOException, InterruptedException {
        String url = toAbsoluteUrl(relativeUrl);
        Set<String> visited = new HashSet<>();
        StringBuilder builder = new StringBuilder();

        while (url != null && visited.add(url)) {
            String html = fetch(url);
            builder.append(cleanContent(extractChapterContent(html)));
            builder.append(System.lineSeparator()).append(System.lineSeparator());

            Optional<Link> next = findNextLink(html);
            if (next.isEmpty() || next.get().type == LinkType.NEXT_CHAPTER) {
                break;
            }

            url = toAbsoluteUrl(next.get().href);
            Thread.sleep(REQUEST_PAUSE_MS);
        }

        return builder.toString().trim();
    }

    private String fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }

        throw new IOException("请求失败，状态码: " + response.statusCode() + ", URL: " + url);
    }

    private Optional<String> extractDivById(String html, String id) {
        String lower = html.toLowerCase(Locale.ROOT);
        String marker = "<div";
        int cursor = 0;
        while (cursor >= 0) {
            int openIdx = lower.indexOf(marker, cursor);
            if (openIdx < 0) {
                return Optional.empty();
            }

            int tagEnd = lower.indexOf('>', openIdx);
            if (tagEnd < 0) {
                return Optional.empty();
            }

            String tag = lower.substring(openIdx, tagEnd + 1);
            if (!tag.contains("id=\"" + id.toLowerCase(Locale.ROOT) + "\"")) {
                cursor = tagEnd + 1;
                continue;
            }

            int contentStart = tagEnd + 1;
            int depth = 1;
            int searchIdx = contentStart;
            while (depth > 0) {
                int nextOpen = lower.indexOf("<div", searchIdx);
                int nextClose = lower.indexOf("</div>", searchIdx);

                if (nextClose < 0) {
                    return Optional.empty();
                }

                if (nextOpen >= 0 && nextOpen < nextClose) {
                    depth++;
                    searchIdx = nextOpen + 4;
                    continue;
                }

                depth--;
                searchIdx = nextClose + 6;
            }

            int contentEnd = searchIdx - 6;
            return Optional.of(html.substring(contentStart, contentEnd));
        }

        return Optional.empty();
    }

    private List<Chapter> parseChapterList(String block) {
        List<Chapter> chapters = new ArrayList<>();
        Matcher matcher = CHAPTER_LINK_PATTERN.matcher(block);
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            String title = decodeHtmlEntities(stripTags(matcher.group(2))).trim();
            if (!href.isEmpty() && !title.isEmpty()) {
                chapters.add(new Chapter(href, title));
            }
        }
        return chapters;
    }

    private String extractChapterContent(String html) {
        return extractDivById(html, "chaptercontent")
                .orElseThrow(() -> new IllegalStateException("未找到章节正文"));
    }

    private Optional<Link> findNextLink(String html) {
        Matcher matcher = NEXT_LINK_PATTERN.matcher(html);
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            String label = matcher.group(2).trim();
            if (href.isEmpty()) {
                continue;
            }
            if (label.contains("下一页")) {
                return Optional.of(new Link(href, LinkType.NEXT_PAGE));
            }
            if (label.contains("下一章")) {
                return Optional.of(new Link(href, LinkType.NEXT_CHAPTER));
            }
        }
        return Optional.empty();
    }

    private String cleanContent(String rawHtml) {
        String withoutScripts = rawHtml.replaceAll("(?is)<script.*?</script>", "");
        String normalizedBreaks = withoutScripts.replaceAll("(?i)<br\\s*/?>", "\n");
        String withoutTags = normalizedBreaks.replaceAll("(?is)<[^>]+>", "\n");
        String decoded = decodeHtmlEntities(withoutTags);
        String[] lines = decoded.replace('\u00A0', ' ').replace('\r', '\n').split("\n");

        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isNoiseLine(trimmed)) {
                flushParagraph(current, paragraphs);
                continue;
            }

            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(trimmed);
        }
        flushParagraph(current, paragraphs);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < paragraphs.size(); i++) {
            if (i > 0) {
                builder.append(System.lineSeparator()).append(System.lineSeparator());
            }
            builder.append(paragraphs.get(i));
        }

        return builder.toString().trim();
    }

    private void flushParagraph(StringBuilder current, List<String> paragraphs) {
        if (current.length() == 0) {
            return;
        }
        paragraphs.add(current.toString().replaceAll("\\s{2,}", " ").trim());
        current.setLength(0);
    }

    private boolean isNoiseLine(String line) {
        return line.contains("本站")
                || line.contains("爱笔楼")
                || line.contains("章节错误")
                || line.contains("加入书签")
                || line.contains("本章未完")
                || line.matches("^[-=]{3,}.*");
    }

    private String stripTags(String html) {
        return html.replaceAll("(?is)<[^>]+>", "");
    }

    private String decodeHtmlEntities(String text) {
        String result = text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");

        Matcher matcher = NUMERIC_ENTITY_PATTERN.matcher(result);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            int codePoint;
            if (value.startsWith("x") || value.startsWith("X")) {
                codePoint = Integer.parseInt(value.substring(1), 16);
            } else {
                codePoint = Integer.parseInt(value, 10);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String sanitizeFileName(String input) {
        String sanitized = input.replaceAll("[\\\\/:*?\"<>|]", "_");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        if (sanitized.isEmpty()) {
            sanitized = "chapter";
        }
        return sanitized;
    }

    private String toAbsoluteUrl(String href) {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        if (!href.startsWith("/")) {
            return BASE_URL + '/' + href;
        }
        return BASE_URL + href;
    }

    private static class Chapter {
        private final String url;
        private final String title;

        private Chapter(String url, String title) {
            this.url = url;
            this.title = title;
        }
    }

    private enum LinkType {
        NEXT_PAGE,
        NEXT_CHAPTER
    }

    private static class Link {
        private final String href;
        private final LinkType type;

        private Link(String href, LinkType type) {
            this.href = href;
            this.type = type;
        }
    }

    private static class Arguments {
        private final Path outputDir;
        private final int fromIndex;
        private final Optional<Integer> toIndex;

        private Arguments(Path outputDir, int fromIndex, Optional<Integer> toIndex) {
            this.outputDir = outputDir;
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
        }

        private static Arguments parse(String[] args) {
            Path output = args.length > 0 ? Paths.get(args[0]) : Paths.get("D:\\杂物\\小说下载路径");
            int from = 1;
            Optional<Integer> to = Optional.empty();

            if (args.length > 1) {
                from = parsePositiveInt(args[1], "起始章节序号");
            }

            if (args.length > 2) {
                to = Optional.of(parsePositiveInt(args[2], "结束章节序号"));
            }

            return new Arguments(output, from, to);
        }

        private static int parsePositiveInt(String raw, String name) {
            try {
                int value = Integer.parseInt(raw);
                if (value <= 0) {
                    throw new IllegalArgumentException(name + "必须为正整数");
                }
                return value;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(name + "格式不正确: " + raw);
            }
        }

        private static void printUsage() {
            System.out.println("用法: java MobileBiquCrawler [输出目录] [起始序号] [结束序号]");
            System.out.println("  输出目录: 可选，默认 D:\\杂物\\小说下载路径");
            System.out.println("  起始序号: 可选，从1开始的章节序号，默认 1");
            System.out.println("  结束序号: 可选，从1开始的章节序号，默认下载到最后一章");
        }
    }
}

