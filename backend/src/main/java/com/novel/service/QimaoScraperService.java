package com.novel.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.dto.QimaoNovelDTO;
import com.novel.dto.QimaoStatisticsDTO;
import com.novel.entity.QimaoCategory;
import com.novel.entity.QimaoNovel;
import com.novel.entity.QimaoScraperTask;
import com.novel.mapper.QimaoCategoryMapper;
import com.novel.mapper.QimaoNovelMapper;
import com.novel.mapper.QimaoScraperTaskMapper;
import com.novel.mapper.QimaoScraperConfigMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 七猫小说爬虫服务
 */
@Slf4j
@Service
public class QimaoScraperService {

    @Autowired
    private QimaoNovelMapper qimaoNovelMapper;

    @Autowired
    private QimaoCategoryMapper qimaoCategoryMapper;

    @Autowired
    private QimaoScraperTaskMapper qimaoScraperTaskMapper;

    @Autowired
    private QimaoScraperConfigMapper configMapper;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public QimaoScraperService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 异步爬取指定分类的小说
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void scrapeCategory(String categoryCode, int maxPages) {
        scrapeCategory(categoryCode, maxPages, false);
    }

    /**
     * 异步爬取指定分类的小说（支持强制爬取）
     */
    @Async
    @Transactional(rollbackFor = Exception.class)
    public void scrapeCategory(String categoryCode, int maxPages, boolean force) {
        QimaoScraperTask task = createTask(categoryCode);
        
        try {
            // 获取分类信息
            QimaoCategory category = qimaoCategoryMapper.selectOne(
                new QueryWrapper<QimaoCategory>().eq("category_code", categoryCode)
            );
            
            if (category == null) {
                throw new RuntimeException("分类不存在: " + categoryCode);
            }



            task.setTaskStatus("RUNNING");
            task.setStartTime(LocalDateTime.now());
            qimaoScraperTaskMapper.updateById(task);

            List<QimaoNovelDTO> allNovels = new ArrayList<>();
            
            // 爬取多页数据
            for (int page = 1; page <= maxPages; page++) {
                try {
                    String url = category.getCategoryUrl().replace("-1/", "-" + page + "/");
                    log.info("正在爬取: {} - 第{}页", category.getCategoryName(), page);
                    
                    List<QimaoNovelDTO> novels = scrapePage(url, category.getCategoryName());
                    allNovels.addAll(novels);
                    
                    // 随机延迟，避免被封
                    Thread.sleep(2000 + new Random().nextInt(3000));
                } catch (Exception e) {
                    log.error("爬取第{}页失败: {}", page, e.getMessage());
                    task.setFailedCount(task.getFailedCount() + 1);
                }
            }

            // 保存小说数据
            int successCount = 0;
            int failedCount = 0;
            
            for (QimaoNovelDTO novelDTO : allNovels) {
                try {
                    saveNovel(novelDTO, category.getCategoryName());
                    successCount++;
                } catch (Exception e) {
                    log.error("保存小说失败: {} - {}", novelDTO.getTitle(), e.getMessage());
                    failedCount++;
                }
            }

            // 更新任务状态
            task.setTaskStatus("COMPLETED");
            task.setTotalNovels(allNovels.size());
            task.setSuccessCount(successCount);
            task.setFailedCount(failedCount);
            task.setEndTime(LocalDateTime.now());
            qimaoScraperTaskMapper.updateById(task);

            // 更新分类的爬取时间
            updateCategoryScrapeTime(category);

            log.info("爬取完成: {} - 总数: {}, 成功: {}, 失败: {}", 
                category.getCategoryName(), allNovels.size(), successCount, failedCount);

        } catch (Exception e) {
            log.error("爬取任务失败: {}", e.getMessage(), e);
            task.setTaskStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setEndTime(LocalDateTime.now());
            qimaoScraperTaskMapper.updateById(task);
        }
    }

    /**
     * 爬取单页数据
     */
    private List<QimaoNovelDTO> scrapePage(String url, String categoryName) throws Exception {
        List<QimaoNovelDTO> novels = new ArrayList<>();
        
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .addHeader("Connection", "keep-alive")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("Cache-Control", "max-age=0")
                .addHeader("Referer", "https://www.qimao.com/")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("HTTP请求失败: {} - {}", response.code(), url);
                throw new RuntimeException("HTTP请求失败: " + response.code());
            }

            String html = response.body().string();
            log.debug("获取到HTML内容，长度: {} 字节", html.length());
            
            Document doc = Jsoup.parse(html);

            // 尝试多种选择器策略
            Elements novelElements = findNovelElements(doc);
            
            log.info("找到 {} 个小说元素", novelElements.size());
            
            if (novelElements.isEmpty()) {
                log.warn("未找到任何小说元素，URL: {}", url);
                // 保存HTML用于调试
                log.debug("HTML内容预览: {}", html.substring(0, Math.min(1000, html.length())));
                return novels;
            }

            int position = 1;
            for (Element element : novelElements) {
                try {
                    QimaoNovelDTO novel = parseNovelElement(element, categoryName, position++);
                    if (novel != null && novel.getTitle() != null && !novel.getTitle().isEmpty()) {
                        novels.add(novel);
                        log.debug("成功解析小说: {}", novel.getTitle());
                    }
                } catch (Exception e) {
                    log.warn("解析小说元素失败: {}", e.getMessage());
                }
            }
        }

        return novels;
    }
    
    /**
     * 使用多种策略查找小说元素
     */
    private Elements findNovelElements(Document doc) {
        // 策略1: 常见的小说列表类名
        String[] selectors = {
            ".book-item",
            ".book-list-item", 
            ".novel-item",
            "li.book-item",
            "div.book-item",
            ".book-info",
            ".booklist li",
            ".book-list li",
            "ul.book-list > li",
            "div[class*='book-item']",
            "li[class*='book']"
        };
        
        for (String selector : selectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                log.debug("使用选择器 '{}' 找到 {} 个元素", selector, elements.size());
                return elements;
            }
        }
        
        // 策略2: 查找包含书名链接的列表项（支持 /book/ 和 /shuku/ 两种链接形式）
        Elements links = doc.select("a[href*='/book/'], a[href*='/shuku/']");
        if (!links.isEmpty()) {
            log.debug("通过书籍/书库链接找到 {} 个元素", links.size());

            // 获取这些链接的父元素，尽量定位到每本书的容器块
            Elements parents = new Elements();
            for (Element link : links) {
                Element parent = link.parent();
                int depth = 0;

                // 向上寻找合适的容器：优先 li、dd、div、p、section、article 等块级元素
                while (parent != null && depth < 5 &&
                        !("li".equals(parent.tagName()) ||
                          "dd".equals(parent.tagName()) ||
                          "div".equals(parent.tagName()) ||
                          "p".equals(parent.tagName()) ||
                          "section".equals(parent.tagName()) ||
                          "article".equals(parent.tagName()))) {
                    parent = parent.parent();
                    depth++;
                }

                // 如果没找到合适的父容器，就直接使用链接自身作为元素
                if (parent == null) {
                    parent = link;
                }

                if (!parents.contains(parent)) {
                    parents.add(parent);
                }
            }

            if (!parents.isEmpty()) {
                return parents;
            }
        }
        
        // 策略3: 返回空列表
        log.warn("所有选择器策略均未找到元素");
        return new Elements();
    }

    /**
     * 解析小说元素
     */
    private QimaoNovelDTO parseNovelElement(Element element, String categoryName, int position) {
        QimaoNovelDTO novel = new QimaoNovelDTO();
        
        try {
            // 提取标题和链接 - 优先匹配 /shuku/ 的书籍链接，其次兼容 /book/
            Element titleElement = element.selectFirst("a[href*='/shuku/']");
            if (titleElement == null) {
                titleElement = element.selectFirst("a[href*='/book/']");
            }
            if (titleElement == null) {
                titleElement = element.selectFirst("h3 a, h4 a, .book-title a, .title a, a.title");
            }

            if (titleElement != null) {
                String title = titleElement.text().trim();
                // 清理标题中的多余空白和换行
                title = title.replaceAll("\\s+", " ").trim();
                
                // 过滤无效标题
                if (isInvalidTitle(title)) {
                    log.debug("过滤无效标题: {}", title);
                    return null;
                }
                
                novel.setTitle(title);
                
                String href = titleElement.attr("href");
                if (href != null && !href.isEmpty()) {
                    if (!href.startsWith("http")) {
                        href = "https://www.qimao.com" + href;
                    }
                    novel.setNovelUrl(href);
                    
                    // 从URL提取小说ID
                    String novelId = extractNovelId(href);
                    novel.setNovelId(novelId);
                }
            } else {
                // 如果没有找到标题链接，尝试直接查找文本
                String text = element.text();
                if (text != null && text.length() > 10) {
                    log.debug("未找到标题链接，元素文本: {}", text.substring(0, Math.min(50, text.length())));
                }
                return null; // 没有标题则跳过
            }

            // 提取作者 - 更灵活的查找
            Element authorElement = element.selectFirst("a[href*='/zuozhe/']");
            if (authorElement == null) {
                authorElement = element.selectFirst(".author a, .book-author a, .author, span:contains(作者)");
            }
            if (authorElement != null) {
                String author = authorElement.text().trim();
                author = author.replace("作者:", "").replace("作者：", "").trim();
                novel.setAuthor(author);
                
                String authorHref = authorElement.attr("href");
                if (authorHref != null && !authorHref.isEmpty()) {
                    if (!authorHref.startsWith("http")) {
                        authorHref = "https://www.qimao.com" + authorHref;
                    }
                    novel.setAuthorUrl(authorHref);
                }
            }

            // 提取简介 - 通常是较长的文本段落
            Element descElement = element.selectFirst(".book-desc, .desc, .intro, .description, p");
            if (descElement != null) {
                String desc = descElement.text().trim();
                if (desc.length() > 20) { // 简介通常较长
                    novel.setDescription(desc);
                }
            }
            
            // 如果上面没找到简介，尝试从整个元素文本中提取较长段落
            if (novel.getDescription() == null || novel.getDescription().isEmpty()) {
                String fullText = element.text();
                // 按句号或换行分割，找最长的段落作为简介
                String[] paragraphs = fullText.split("[。\\n]");
                String longestParagraph = "";
                for (String para : paragraphs) {
                    para = para.trim();
                    if (para.length() > longestParagraph.length() && para.length() > 30 && para.length() < 500) {
                        longestParagraph = para;
                    }
                }
                if (!longestParagraph.isEmpty()) {
                    novel.setDescription(longestParagraph);
                }
            }

            // 提取字数 - 包含"万字"的文本
            String elementText = element.text();
            if (elementText.contains("万字")) {
                // 提取字数信息
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([\\d.]+万字)");
                java.util.regex.Matcher matcher = pattern.matcher(elementText);
                if (matcher.find()) {
                    novel.setWordCount(matcher.group(1));
                }
            }

            // 提取状态
            if (elementText.contains("完结")) {
                novel.setStatus("完结");
            } else if (elementText.contains("连载")) {
                novel.setStatus("连载中");
            } else {
                Element statusElement = element.selectFirst(".status, .book-status");
                if (statusElement != null) {
                    novel.setStatus(statusElement.text().trim());
                }
            }

            // 提取更新时间
            if (elementText.contains("更新")) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2}[^\\s]*更新)");
                java.util.regex.Matcher matcher = pattern.matcher(elementText);
                if (matcher.find()) {
                    novel.setUpdateTime(matcher.group(1));
                }
            }

            // 提取封面
            Element coverElement = element.selectFirst("img");
            if (coverElement != null) {
                String coverUrl = coverElement.attr("src");
                if (coverUrl == null || coverUrl.isEmpty()) {
                    coverUrl = coverElement.attr("data-src");
                }
                if (coverUrl == null || coverUrl.isEmpty()) {
                    coverUrl = coverElement.attr("data-original");
                }
                if (coverUrl != null && !coverUrl.isEmpty()) {
                    novel.setCoverImageUrl(coverUrl);
                }
            }

            // 提取标签 - 查找所有可能的标签元素
            Elements tagElements = element.select(".tag, .label, .book-tag, span.tag");
            if (!tagElements.isEmpty()) {
                List<String> tags = tagElements.stream()
                        .map(Element::text)
                        .map(String::trim)
                        .filter(tag -> !tag.isEmpty() && tag.length() < 20) // 过滤掉过长的文本
                        .collect(Collectors.toList());
                if (!tags.isEmpty()) {
                    novel.setTags(tags);
                }
            }

            // 提取阅读次数/人气 - 查找包含"万"、"亿"、"次"、"人气"等关键词的文本
            if (elementText.contains("万") || elementText.contains("亿")) {
                // 尝试提取阅读量
                java.util.regex.Pattern readPattern = java.util.regex.Pattern.compile("([\\d.]+[万亿]次|[\\d.]+[万亿]阅读|阅读[\\d.]+[万亿])");
                java.util.regex.Matcher readMatcher = readPattern.matcher(elementText);
                if (readMatcher.find()) {
                    // 这里可以存到一个自定义字段，暂时存到updateTime或新增字段
                    log.debug("提取到阅读量: {}", readMatcher.group(1));
                }
                
                // 尝试提取人气
                java.util.regex.Pattern popularityPattern = java.util.regex.Pattern.compile("(人气[\\d.]+[万亿]|[\\d.]+[万亿]人气)");
                java.util.regex.Matcher popularityMatcher = popularityPattern.matcher(elementText);
                if (popularityMatcher.find()) {
                    log.debug("提取到人气: {}", popularityMatcher.group(1));
                }
            }

            novel.setCategory(categoryName);
            novel.setRankPosition(position);
            novel.setRankType("点击榜");

            return novel;
        } catch (Exception e) {
            log.warn("解析小说详情失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 爬取小说前N章内容（公开方法，供Controller调用）
     */
    public Map<String, Object> scrapeChapters(String novelId, int chapterCount) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 从数据库获取小说信息
            QimaoNovel novel = qimaoNovelMapper.selectOne(
                new QueryWrapper<QimaoNovel>().eq("novel_id", novelId)
            );
            
            if (novel == null) {
                result.put("success", false);
                result.put("message", "小说不存在");
                return result;
            }
            
            String novelUrl = novel.getNovelUrl();
            if (novelUrl == null || novelUrl.isEmpty()) {
                result.put("success", false);
                result.put("message", "小说URL为空");
                return result;
            }
            
            log.info("开始爬取小说章节: {} - {}", novel.getTitle(), novelUrl);
            
            // 获取章节列表
            List<Map<String, String>> chapters = scrapeChapterList(novelUrl, chapterCount);
            
            if (chapters.isEmpty()) {
                result.put("success", false);
                result.put("message", "未找到章节列表");
                return result;
            }
            
            // 爬取每一章的内容
            List<Map<String, Object>> chapterContents = new ArrayList<>();
            for (int i = 0; i < Math.min(chapters.size(), chapterCount); i++) {
                Map<String, String> chapter = chapters.get(i);
                try {
                    String content = scrapeChapterContent(chapter.get("url"));
                    Map<String, Object> chapterData = new HashMap<>();
                    chapterData.put("title", chapter.get("title"));
                    chapterData.put("url", chapter.get("url"));
                    chapterData.put("content", content);
                    chapterData.put("wordCount", content != null ? content.length() : 0);
                    chapterContents.add(chapterData);
                    
                    // 延迟避免请求过快
                    if (i < chapters.size() - 1) {
                        Thread.sleep(1000 + new Random().nextInt(1000));
                    }
                } catch (Exception e) {
                    log.error("爬取章节失败: {} - {}", chapter.get("title"), e.getMessage());
                }
            }
            
            result.put("success", true);
            result.put("novelId", novelId);
            result.put("novelTitle", novel.getTitle());
            result.put("chapters", chapterContents);
            result.put("totalChapters", chapterContents.size());
            
            return result;
        } catch (Exception e) {
            log.error("爬取章节失败", e);
            result.put("success", false);
            result.put("message", "爬取失败: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 获取小说章节列表
     */
    private List<Map<String, String>> scrapeChapterList(String novelUrl, int maxCount) throws Exception {
        List<Map<String, String>> chapters = new ArrayList<>();
        
        Request request = new Request.Builder()
                .url(novelUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return chapters;
            }

            String html = response.body().string();
            Document doc = Jsoup.parse(html);

            // 查找章节列表 - 尝试多种选择器
            Elements chapterLinks = doc.select("a[href*='/chapter/'], .chapter-list a, .catalog a, .chapter a, .directory a");
            
            if (chapterLinks.isEmpty()) {
                // 尝试查找包含"第"和"章"的链接
                chapterLinks = doc.select("a:contains(第):contains(章)");
            }
            
            int count = 0;
            for (Element link : chapterLinks) {
                if (count >= maxCount) {
                    break;
                }
                
                String chapterTitle = link.text().trim();
                String chapterUrl = link.attr("href");
                
                if (chapterUrl != null && !chapterUrl.isEmpty()) {
                    if (!chapterUrl.startsWith("http")) {
                        chapterUrl = "https://www.qimao.com" + chapterUrl;
                    }
                    
                    Map<String, String> chapter = new HashMap<>();
                    chapter.put("title", chapterTitle);
                    chapter.put("url", chapterUrl);
                    chapters.add(chapter);
                    count++;
                }
            }
        }
        
        return chapters;
    }
    
    /**
     * 爬取单个章节内容
     */
    private String scrapeChapterContent(String chapterUrl) throws Exception {
        Request request = new Request.Builder()
                .url(chapterUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return null;
            }

            String html = response.body().string();
            Document doc = Jsoup.parse(html);
            
            // 查找章节内容 - 尝试多种选择器
            Element contentElement = doc.selectFirst(".chapter-content, .content, .read-content, .txt-content, #content, .book-content");
            
            if (contentElement != null) {
                // 清理内容中的广告和无关元素
                contentElement.select("script, style, .ad, .advertisement").remove();
                return contentElement.text();
            }
        }
        
        return null;
    }

    /**
     * 爬取小说第一章内容（私有方法，用于初次爬取时保存）
     */
    private String scrapeFirstChapter(String novelUrl) {
        try {
            // 先获取小说详情页
            Request request = new Request.Builder()
                    .url(novelUrl)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return null;
                }

                String html = response.body().string();
                Document doc = Jsoup.parse(html);

                // 查找第一章链接
                Element firstChapterLink = doc.selectFirst("a[href*='/chapter/'], .chapter-list a:first-child, .catalog a:first-child");
                
                if (firstChapterLink != null) {
                    String chapterUrl = firstChapterLink.attr("href");
                    if (!chapterUrl.startsWith("http")) {
                        chapterUrl = "https://www.qimao.com" + chapterUrl;
                    }

                    // 延迟避免频繁请求
                    Thread.sleep(1000);

                    // 获取章节内容
                    Request chapterRequest = new Request.Builder()
                            .url(chapterUrl)
                            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build();

                    try (Response chapterResponse = httpClient.newCall(chapterRequest).execute()) {
                        if (chapterResponse.isSuccessful()) {
                            String chapterHtml = chapterResponse.body().string();
                            Document chapterDoc = Jsoup.parse(chapterHtml);
                            
                            Element contentElement = chapterDoc.selectFirst(".chapter-content, .content, .read-content");
                            if (contentElement != null) {
                                return contentElement.text();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("爬取第一章失败: {} - {}", novelUrl, e.getMessage());
        }
        
        return null;
    }

    /**
     * 保存小说数据
     */
    private void saveNovel(QimaoNovelDTO novelDTO, String categoryName) throws Exception {
        // 检查是否已存在
        QueryWrapper<QimaoNovel> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelDTO.getNovelId());
        QimaoNovel existingNovel = qimaoNovelMapper.selectOne(queryWrapper);

        QimaoNovel novel = new QimaoNovel();
        if (existingNovel != null) {
            novel = existingNovel;
        }

        // 复制基本属性
        BeanUtils.copyProperties(novelDTO, novel);
        
        // 转换标签为JSON
        if (novelDTO.getTags() != null && !novelDTO.getTags().isEmpty()) {
            novel.setTags(objectMapper.writeValueAsString(novelDTO.getTags()));
        }

        // 尝试爬取第一章（可选，避免过多请求）
        if (existingNovel == null && novelDTO.getNovelUrl() != null) {
            String firstChapterContent = scrapeFirstChapter(novelDTO.getNovelUrl());
            if (firstChapterContent != null && !firstChapterContent.isEmpty()) {
                novel.setFirstChapterContent(firstChapterContent);
                novel.setFirstChapterTitle("第一章");
            }
        }

        if (existingNovel == null) {
            qimaoNovelMapper.insert(novel);
        } else {
            qimaoNovelMapper.updateById(novel);
        }
    }

    /**
     * 判断标题是否无效（过滤导航链接等）
     */
    private boolean isInvalidTitle(String title) {
        if (title == null || title.isEmpty()) {
            return true;
        }
        
        // 过滤常见的无效标题
        String[] invalidTitles = {
            "立即登录", "登录", "注册", "首页", "分类", "排行榜", 
            "征文活动", "作家专区", "签约政策", "投稿指南", "剧本征集",
            "联系我们", "关于七猫", "七猫招聘", "搜索", "全部",
            "女生原创", "男生原创", "出版图书", "现代言情", "古代言情",
            "总裁豪门", "职场情缘", "民国旧影", "娱乐明星", "现实生活",
            "青春校园", "年代重生", "都市奇幻", "现代悬疑", "幻想言情"
        };
        
        for (String invalid : invalidTitles) {
            if (title.equals(invalid) || title.contains(invalid)) {
                return true;
            }
        }
        
        // 标题太短（少于2个字）或太长（超过50个字）
        if (title.length() < 2 || title.length() > 50) {
            return true;
        }
        
        return false;
    }

    /**
     * 从URL提取小说ID
     */
    private String extractNovelId(String url) {
        try {
            // 从URL中提取ID，例如: /book/123456 -> 123456
            String[] parts = url.split("/");
            for (String part : parts) {
                if (part.matches("\\d+")) {
                    return part;
                }
            }
            // 如果没有数字ID，使用URL的hash
            return String.valueOf(url.hashCode());
        } catch (Exception e) {
            return String.valueOf(url.hashCode());
        }
    }

    /**
     * 创建爬虫任务
     */
    private QimaoScraperTask createTask(String categoryCode) {
        QimaoScraperTask task = new QimaoScraperTask();
        task.setTaskName("爬取分类: " + categoryCode);
        task.setCategoryCode(categoryCode);
        task.setTaskStatus("PENDING");
        task.setTotalNovels(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        qimaoScraperTaskMapper.insert(task);
        return task;
    }

    /**
     * 获取所有分类
     */
    public List<QimaoCategory> getAllCategories() {
        QueryWrapper<QimaoCategory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_active", true);
        queryWrapper.orderByAsc("sort_order");
        return qimaoCategoryMapper.selectList(queryWrapper);
    }

    /**
     * 获取统计数据
     */
    public QimaoStatisticsDTO getStatistics() {
        QimaoStatisticsDTO stats = new QimaoStatisticsDTO();
        
        // 总数
        stats.setTotalNovels(qimaoNovelMapper.selectCount(null));
        
        // 分类统计
        stats.setCategoryStats(qimaoNovelMapper.countByCategory());
        
        // 状态统计
        stats.setStatusStats(qimaoNovelMapper.countByStatus());
        
        // 作者统计
        stats.setAuthorStats(qimaoNovelMapper.countByAuthor());
        
        // 排行榜类型统计
        stats.setRankTypeStats(qimaoNovelMapper.countByRankType());
        
        // 最近小说
        List<QimaoNovel> recentNovels = qimaoNovelMapper.getRecentNovels(10);
        List<QimaoNovelDTO> recentDTOs = recentNovels.stream().map(novel -> {
            QimaoNovelDTO dto = new QimaoNovelDTO();
            BeanUtils.copyProperties(novel, dto);
            try {
                if (novel.getTags() != null && !novel.getTags().isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<String> tags = objectMapper.readValue(novel.getTags(), List.class);
                    dto.setTags(tags);
                }
            } catch (Exception e) {
                log.warn("解析标签失败: {}", e.getMessage());
            }
            return dto;
        }).collect(Collectors.toList());
        stats.setRecentNovels(recentDTOs);
        
        // 任务统计
        Map<String, Object> taskStats = new HashMap<>();
        taskStats.put("total", qimaoScraperTaskMapper.selectCount(null));
        taskStats.put("completed", qimaoScraperTaskMapper.selectCount(
            new QueryWrapper<QimaoScraperTask>().eq("task_status", "COMPLETED")
        ));
        taskStats.put("running", qimaoScraperTaskMapper.selectCount(
            new QueryWrapper<QimaoScraperTask>().eq("task_status", "RUNNING")
        ));
        taskStats.put("failed", qimaoScraperTaskMapper.selectCount(
            new QueryWrapper<QimaoScraperTask>().eq("task_status", "FAILED")
        ));
        stats.setTaskStats(taskStats);
        
        return stats;
    }

    /**
     * 获取小说列表
     */
    public List<QimaoNovelDTO> getNovels(String category, String status, int page, int pageSize) {
        QueryWrapper<QimaoNovel> queryWrapper = new QueryWrapper<>();
        
        if (category != null && !category.isEmpty()) {
            queryWrapper.eq("category", category);
        }
        
        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        }
        
        queryWrapper.orderByDesc("created_at");
        queryWrapper.last("LIMIT " + ((page - 1) * pageSize) + ", " + pageSize);
        
        List<QimaoNovel> novels = qimaoNovelMapper.selectList(queryWrapper);
        
        return novels.stream().map(novel -> {
            QimaoNovelDTO dto = new QimaoNovelDTO();
            BeanUtils.copyProperties(novel, dto);
            try {
                if (novel.getTags() != null && !novel.getTags().isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<String> tags = objectMapper.readValue(novel.getTags(), List.class);
                    dto.setTags(tags);
                }
            } catch (Exception e) {
                log.warn("解析标签失败: {}", e.getMessage());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 获取任务列表
     */
    public List<QimaoScraperTask> getTasks(int page, int pageSize) {
        QueryWrapper<QimaoScraperTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("created_at");
        queryWrapper.last("LIMIT " + ((page - 1) * pageSize) + ", " + pageSize);
        return qimaoScraperTaskMapper.selectList(queryWrapper);
    }

    /**
     * 检查分类是否需要爬取
     */
    private boolean shouldScrape(QimaoCategory category) {
        try {
            // 如果从未爬取过，需要爬取
            if (category.getLastScrapeTime() == null) {
                return true;
            }

            // 获取爬取间隔配置（小时）
            String intervalHoursStr = configMapper.getConfigValue("scrape_interval_hours");
            int intervalHours = intervalHoursStr != null ? Integer.parseInt(intervalHoursStr) : 24;

            // 计算距离上次爬取的时间
            LocalDateTime threshold = LocalDateTime.now().minusHours(intervalHours);
            
            // 如果上次爬取时间早于阈值，需要爬取
            return category.getLastScrapeTime().isBefore(threshold);
        } catch (Exception e) {
            log.error("检查爬取条件失败", e);
            return false;
        }
    }

    /**
     * 更新分类的爬取时间
     */
    private void updateCategoryScrapeTime(QimaoCategory category) {
        try {
            category.setLastScrapeTime(LocalDateTime.now());
            category.setScrapeCount((category.getScrapeCount() != null ? category.getScrapeCount() : 0) + 1);
            qimaoCategoryMapper.updateById(category);
        } catch (Exception e) {
            log.error("更新分类爬取时间失败", e);
        }
    }

    /**
     * 测试抓取单个页面（调试用）
     */
    public Map<String, Object> testScrapePage(String url, String categoryName) throws Exception {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 发送HTTP请求
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .addHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Referer", "https://www.qimao.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                result.put("httpStatus", response.code());
                result.put("httpMessage", response.message());
                
                if (!response.isSuccessful()) {
                    result.put("success", false);
                    result.put("message", "HTTP请求失败: " + response.code());
                    return result;
                }

                String html = response.body().string();
                result.put("htmlLength", html.length());
                result.put("htmlPreview", html.substring(0, Math.min(500, html.length())));

                // 解析HTML
                Document doc = Jsoup.parse(html);
                
                // 查找小说元素
                Elements novelElements = findNovelElements(doc);
                result.put("elementsFound", novelElements.size());

                // 尝试解析小说
                List<Map<String, Object>> novels = new ArrayList<>();
                int position = 1;
                for (Element element : novelElements) {
                    try {
                        QimaoNovelDTO novel = parseNovelElement(element, categoryName, position++);
                        if (novel != null && novel.getTitle() != null) {
                            Map<String, Object> novelMap = new HashMap<>();
                            novelMap.put("title", novel.getTitle());
                            novelMap.put("author", novel.getAuthor());
                            novelMap.put("url", novel.getNovelUrl());
                            novelMap.put("description", novel.getDescription());
                            novelMap.put("wordCount", novel.getWordCount());
                            novelMap.put("status", novel.getStatus());
                            novelMap.put("updateTime", novel.getUpdateTime());
                            novelMap.put("coverImage", novel.getCoverImageUrl());
                            novelMap.put("tags", novel.getTags());
                            novels.add(novelMap);
                        }
                    } catch (Exception e) {
                        log.warn("解析小说元素失败: {}", e.getMessage());
                    }
                    
                    // 最多返回10个示例
                    if (novels.size() >= 10) {
                        break;
                    }
                }

                result.put("success", true);
                result.put("novelsParsed", novels.size());
                result.put("novels", novels);
                
                // 如果没有解析到小说，添加调试信息
                if (novels.isEmpty()) {
                    result.put("debugInfo", "未能解析到小说，可能的原因：");
                    result.put("possibleReasons", Arrays.asList(
                        "1. 页面是JavaScript动态加载的",
                        "2. CSS选择器不匹配",
                        "3. 页面结构发生了变化",
                        "4. 需要登录或Cookie"
                    ));
                    
                    // 输出所有a标签
                    Elements allLinks = doc.select("a[href]");
                    result.put("totalLinks", allLinks.size());
                    
                    // 输出包含/book/的链接
                    Elements bookLinks = doc.select("a[href*='/book/']");
                    result.put("bookLinks", bookLinks.size());
                    
                    // 输出第一个包含/book/的链接的HTML
                    if (!bookLinks.isEmpty()) {
                        Element firstBookLink = bookLinks.first();
                        result.put("firstBookLinkHtml", firstBookLink.outerHtml());
                        result.put("firstBookLinkParent", firstBookLink.parent().outerHtml());
                    }
                }

                return result;
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "测试失败: " + e.getMessage());
            result.put("exception", e.toString());
            log.error("测试抓取失败", e);
            return result;
        }
    }
}
