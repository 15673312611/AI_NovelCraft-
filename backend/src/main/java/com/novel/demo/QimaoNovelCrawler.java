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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 七猫小说网爬虫 - 爬取小说名称和简介
 * 使用方法: java QimaoNovelCrawler [起始页码] [结束页码] [输出文件路径]
 */
public class QimaoNovelCrawler {

    // 基础URL模板 - 总裁豪门分类，连载中，按点击量排序
    private static final String URL_TEMPLATE = "https://www.qimao.com/shuku/1-1-8-a-a-a-0-click-%d/";
    
    // 正则表达式 - 匹配作者
    private static final Pattern AUTHOR_PATTERN = Pattern.compile(
            "<a[^>]+class=\"[^\"]*author[^\"]*\"[^>]*>([^<]+)</a>",
            Pattern.DOTALL
    );
    
    // 正则表达式 - 匹配字数
    private static final Pattern WORD_COUNT_PATTERN = Pattern.compile(
            "([\\d.]+万字)",
            Pattern.DOTALL
    );
    
    // 正则表达式 - 匹配状态（连载中/已完结）
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            "(连载中|已完结)",
            Pattern.DOTALL
    );
    
    private static final int REQUEST_PAUSE_MS = 2000; // 请求间隔2秒，避免请求过快
    
    private final HttpClient client;

    public QimaoNovelCrawler() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    public static void main(String[] args) {
        try {
            Arguments arguments = Arguments.parse(args);
            QimaoNovelCrawler crawler = new QimaoNovelCrawler();
            crawler.run(arguments);
        } catch (IllegalArgumentException ex) {
            System.err.println("参数错误: " + ex.getMessage());
            Arguments.printUsage();
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("执行失败: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(2);
        }
    }

    private void run(Arguments arguments) throws IOException, InterruptedException {
        List<Novel> allNovels = new ArrayList<>();
        
        System.out.println("========================================");
        System.out.println("七猫小说网爬虫启动");
        System.out.println("分类: 总裁豪门 | 状态: 连载中 | 排序: 点击量");
        System.out.println("页码范围: " + arguments.startPage + " - " + arguments.endPage);
        System.out.println("请求间隔: 2秒");
        System.out.println("========================================\n");

        for (int page = arguments.startPage; page <= arguments.endPage; page++) {
            String url = String.format(Locale.ROOT, URL_TEMPLATE, page);
            System.out.printf("正在爬取第 %d 页: %s%n", page, url);
            
            try {
                String html = fetch(url);
                List<Novel> novels = parseNovels(html, page);
                allNovels.addAll(novels);
                System.out.printf("✓ 第 %d 页完成，获取到 %d 本小说%n%n", page, novels.size());
                
                // 避免请求过快
                if (page < arguments.endPage) {
                    Thread.sleep(REQUEST_PAUSE_MS);
                }
            } catch (Exception ex) {
                System.err.printf("✗ 第 %d 页爬取失败: %s%n%n", page, ex.getMessage());
            }
        }

        // 保存结果
        saveToFile(allNovels, arguments.outputFile);
        
        System.out.println("========================================");
        System.out.printf("爬取完成！共获取 %d 本小说%n", allNovels.size());
        System.out.println("保存路径: " + arguments.outputFile.toAbsolutePath());
        System.out.println("========================================");
    }

    private String fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "https://www.qimao.com/")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }

        throw new IOException("请求失败，状态码: " + response.statusCode() + ", URL: " + url);
    }

    private List<Novel> parseNovels(String html, int page) {
        List<Novel> novels = new ArrayList<>();
        
        // 尝试多种分割方式来匹配小说信息块
        String[] patterns = {
            "book-layout",
            "book-item",
            "book-img-box",
            "<li"
        };
        
        String[] blocks = null;
        String usedPattern = null;
        
        for (String pattern : patterns) {
            String[] temp = html.split(pattern);
            if (temp.length > 1) {
                blocks = temp;
                usedPattern = pattern;
                break;
            }
        }
        
        if (blocks == null || blocks.length <= 1) {
            System.err.println("  未能找到小说信息块，尝试保存HTML用于调试...");
            saveDebugHtml(html, page);
            return novels;
        }
        
        System.out.printf("  使用模式 '%s' 找到 %d 个块%n", usedPattern, blocks.length - 1);
        
        for (int i = 1; i < blocks.length; i++) {
            String block = blocks[i];
            
            try {
                Novel novel = parseNovelBlock(block, page, i);
                if (novel != null) {
                    novels.add(novel);
                    System.out.printf("  ✓ 解析成功: %s%n", novel.title);
                }
            } catch (Exception ex) {
                // 静默处理解析失败
            }
        }
        
        return novels;
    }

    private Novel parseNovelBlock(String block, int page, int index) {
        Novel novel = new Novel();
        novel.page = page;
        
        // 尝试多种标题匹配模式
        Pattern[] titlePatterns = {
            Pattern.compile("<h3[^>]*>\\s*<a[^>]+>([^<]+)</a>\\s*</h3>", Pattern.DOTALL),
            Pattern.compile("<h3[^>]*>([^<]+)</h3>", Pattern.DOTALL),
            Pattern.compile("title=\"([^\"]+)\"", Pattern.DOTALL),
            Pattern.compile("alt=\"([^\"]+)\"", Pattern.DOTALL)
        };
        
        for (Pattern p : titlePatterns) {
            Matcher m = p.matcher(block);
            if (m.find()) {
                novel.title = cleanText(m.group(1));
                if (!novel.title.isEmpty()) break;
            }
        }
        
        // 提取简介 - 使用多种策略
        novel.description = extractDescription(block);
        
        // 提取作者
        Matcher authorMatcher = AUTHOR_PATTERN.matcher(block);
        if (authorMatcher.find()) {
            novel.author = cleanText(authorMatcher.group(1));
        }
        
        // 提取字数
        Matcher wordCountMatcher = WORD_COUNT_PATTERN.matcher(block);
        if (wordCountMatcher.find()) {
            novel.wordCount = wordCountMatcher.group(1);
        }
        
        // 提取状态
        Matcher statusMatcher = STATUS_PATTERN.matcher(block);
        if (statusMatcher.find()) {
            novel.status = statusMatcher.group(1);
        }
        
        // 过滤掉无效的小说信息
        if (novel.title == null || novel.title.isEmpty()) {
            return null;
        }
        
        // 过滤掉导航链接、广告等非小说内容
        if (isNoiseTitle(novel.title)) {
            return null;
        }
        
        // 如果没有作者信息，很可能不是小说
        if (novel.author == null || novel.author.isEmpty()) {
            return null;
        }
        
        return novel;
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }
        
        // 解码HTML实体
        text = text.replace("&nbsp;", " ")
                   .replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&#39;", "'")
                   .replace("&ldquo;", "\u201C")
                   .replace("&rdquo;", "\u201D");
        
        // 移除多余空白
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    }

    private String extractDescription(String block) {
        // 策略1: 查找带有intro class的元素
        Pattern[] introPatterns = {
            Pattern.compile("class=\"[^\"]*intro[^\"]*\"[^>]*>\\s*([^<]+)", Pattern.DOTALL),
            Pattern.compile("<p[^>]*class=\"[^\"]*intro[^\"]*\"[^>]*>([^<]+)</p>", Pattern.DOTALL),
            Pattern.compile("<div[^>]*class=\"[^\"]*intro[^\"]*\"[^>]*>\\s*<p[^>]*>([^<]+)</p>", Pattern.DOTALL)
        };
        
        for (Pattern p : introPatterns) {
            Matcher m = p.matcher(block);
            if (m.find()) {
                String desc = cleanText(m.group(1));
                if (desc.length() >= 20 && !isNoiseText(desc)) {
                    return desc;
                }
            }
        }
        
        // 策略2: 查找所有段落文本，选择最长且符合条件的
        Pattern paragraphPattern = Pattern.compile(">\\s*([\\u4e00-\\u9fa5\\uff0c\\u3002\\uff1f\\uff01\\uff1a\\uff1b\\u201c\\u201d\\u300a\\u300b\\u3001\\u2014\\u2014a-zA-Z0-9\\s]{30,800}?)\\s*<", Pattern.DOTALL);
        Matcher paragraphMatcher = paragraphPattern.matcher(block);
        
        String longestDesc = "";
        while (paragraphMatcher.find()) {
            String text = cleanText(paragraphMatcher.group(1));
            // 过滤掉不是简介的内容
            if (text.length() >= 30 && 
                !isNoiseText(text) && 
                !text.matches(".*\\d{4}-\\d{2}-\\d{2}.*") && // 不包含日期
                !text.matches(".*更新.*") && // 不包含"更新"
                text.length() > longestDesc.length()) {
                longestDesc = text;
            }
        }
        
        if (!longestDesc.isEmpty()) {
            // 截取前500个字符作为简介
            return longestDesc.length() > 500 ? longestDesc.substring(0, 500) + "..." : longestDesc;
        }
        
        return "";
    }

    private boolean isNoiseTitle(String title) {
        if (title == null) return true;
        
        String lower = title.toLowerCase();
        // 过滤掉常见的非小说标题
        return lower.contains("七猫") ||
               lower.contains("网警") ||
               lower.contains("举报") ||
               lower.contains("app") ||
               lower.contains("助手") ||
               lower.contains("中文网") ||
               lower.contains("免费小说") ||
               title.length() < 3 || // 标题太短
               title.length() > 50;   // 标题太长
    }
    
    private boolean isNoiseText(String text) {
        if (text == null) return true;
        
        String lower = text.toLowerCase();
        // 过滤掉明显不是简介的文本
        return lower.contains("网警") ||
               lower.contains("举报") ||
               lower.contains("违法") ||
               lower.contains("app下载") ||
               lower.contains("扫码") ||
               lower.contains("关注");
    }

    private void saveDebugHtml(String html, int page) {
        try {
            Path debugFile = Paths.get("D:\\七猫调试_第" + page + "页.html");
            Files.writeString(debugFile, html, StandardCharsets.UTF_8);
            System.out.println("  调试HTML已保存到: " + debugFile);
        } catch (Exception e) {
            // 忽略保存失败
        }
    }

    private void saveToFile(List<Novel> novels, Path outputFile) throws IOException {
        StringBuilder content = new StringBuilder();
        
        // 添加文件头
        content.append("========================================\n");
        content.append("七猫小说网 - 总裁豪门分类小说列表\n");
        content.append("爬取时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        content.append("总计: ").append(novels.size()).append(" 本小说\n");
        content.append("========================================\n\n");
        
        // 添加每本小说的信息
        for (int i = 0; i < novels.size(); i++) {
            Novel novel = novels.get(i);
            content.append("【").append(i + 1).append("】 ").append(novel.title).append("\n");
            
            if (novel.author != null && !novel.author.isEmpty()) {
                content.append("作者: ").append(novel.author).append("\n");
            }
            
            if (novel.status != null && !novel.status.isEmpty()) {
                content.append("状态: ").append(novel.status);
                if (novel.wordCount != null && !novel.wordCount.isEmpty()) {
                    content.append(" | 字数: ").append(novel.wordCount);
                }
                content.append("\n");
            }
            
            if (novel.description != null && !novel.description.isEmpty()) {
                content.append("简介: ").append(novel.description).append("\n");
            }
            
            content.append("来源: 第").append(novel.page).append("页\n");
            content.append("\n");
            content.append("----------------------------------------\n\n");
        }
        
        // 写入文件
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, content.toString(), StandardCharsets.UTF_8);
    }

    private static class Novel {
        int page;           // 所在页码
        String title;       // 小说标题
        String author;      // 作者
        String description; // 简介
        String wordCount;   // 字数
        String status;      // 状态（连载中/已完结）
    }

    private static class Arguments {
        private final int startPage;
        private final int endPage;
        private final Path outputFile;

        private Arguments(int startPage, int endPage, Path outputFile) {
            this.startPage = startPage;
            this.endPage = endPage;
            this.outputFile = outputFile;
        }

        private static Arguments parse(String[] args) {
            int start = 1;
            int end = 5;
            Path output = Paths.get("D:\\七猫小说爬取结果.txt");

            if (args.length > 0) {
                start = parsePositiveInt(args[0], "起始页码");
            }

            if (args.length > 1) {
                end = parsePositiveInt(args[1], "结束页码");
            }

            if (args.length > 2) {
                output = Paths.get(args[2]);
            }

            if (start > end) {
                throw new IllegalArgumentException("起始页码不能大于结束页码");
            }

            return new Arguments(start, end, output);
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
            System.out.println("========================================");
            System.out.println("七猫小说网爬虫 - 使用说明");
            System.out.println("========================================");
            System.out.println("用法: java QimaoNovelCrawler [起始页码] [结束页码] [输出文件路径]");
            System.out.println();
            System.out.println("当前配置:");
            System.out.println("  分类: 总裁豪门");
            System.out.println("  状态: 连载中");
            System.out.println("  排序: 点击量");
            System.out.println("  请求间隔: 2秒");
            System.out.println();
            System.out.println("参数说明:");
            System.out.println("  起始页码    : 可选，默认为 1");
            System.out.println("  结束页码    : 可选，默认为 5");
            System.out.println("  输出文件路径: 可选，默认为 D:\\七猫小说爬取结果.txt");
            System.out.println();
            System.out.println("示例:");
            System.out.println("  java QimaoNovelCrawler");
            System.out.println("  java QimaoNovelCrawler 1 10");
            System.out.println("  java QimaoNovelCrawler 1 10 D:\\总裁豪门小说.txt");
            System.out.println("========================================");
        }
    }
}
