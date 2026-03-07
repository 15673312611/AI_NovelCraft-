# 七猫爬虫修复方案

## 问题分析

七猫小说网站（www.qimao.com）使用了 JavaScript 动态渲染，现有的 Jsoup 静态解析方案无法获取小说数据。

## 解决方案

### 方案1：使用 Selenium WebDriver（推荐）

#### 1. 添加 Maven 依赖

在 `backend/pom.xml` 中添加：

```xml
<!-- Selenium WebDriver -->
<dependency>
    <groupId>org.seleniumhq.selenium</groupId>
    <artifactId>selenium-java</artifactId>
    <version>4.15.0</version>
</dependency>

<!-- WebDriverManager（自动管理ChromeDriver版本）-->
<dependency>
    <groupId>io.github.bonigarcia</groupId>
    <artifactId>webdrivermanager</artifactId>
    <version>5.6.2</version>
</dependency>
```

#### 2. 修改 QimaoScraperService

在 `scrapePage` 方法中使用 Selenium：

```java
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;

private List<QimaoNovelDTO> scrapePage(String url, String categoryName) throws Exception {
    List<QimaoNovelDTO> novels = new ArrayList<>();
    
    // 初始化 ChromeDriver
    WebDriverManager.chromedriver().setup();
    
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless"); // 无头模式
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--disable-gpu");
    options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
    
    WebDriver driver = new ChromeDriver(options);
    
    try {
        driver.get(url);
        
        // 等待页面加载完成
        Thread.sleep(3000);
        
        // 查找小说列表元素（需要根据实际页面结构调整选择器）
        List<WebElement> novelElements = driver.findElements(By.cssSelector("li[class*='book'], div[class*='book']"));
        
        int position = 1;
        for (WebElement element : novelElements) {
            try {
                QimaoNovelDTO novel = new QimaoNovelDTO();
                
                // 提取标题和链接
                WebElement titleElement = element.findElement(By.cssSelector("a"));
                novel.setTitle(titleElement.getText().trim());
                novel.setNovelUrl(titleElement.getAttribute("href"));
                novel.setNovelId(extractNovelId(novel.getNovelUrl()));
                
                // 提取作者
                try {
                    WebElement authorElement = element.findElement(By.cssSelector("a[href*='/zuozhe/']"));
                    novel.setAuthor(authorElement.getText().trim());
                    novel.setAuthorUrl(authorElement.getAttribute("href"));
                } catch (Exception e) {
                    // 作者可能不存在
                }
                
                // 提取简介
                try {
                    WebElement descElement = element.findElement(By.cssSelector("p, div[class*='desc']"));
                    novel.setDescription(descElement.getText().trim());
                } catch (Exception e) {
                    // 简介可能不存在
                }
                
                // 提取字数和状态
                String fullText = element.getText();
                if (fullText.contains("万字")) {
                    int start = fullText.indexOf("万字") - 10;
                    if (start < 0) start = 0;
                    String wordCountPart = fullText.substring(start, fullText.indexOf("万字") + 2);
                    novel.setWordCount(wordCountPart.trim());
                }
                
                if (fullText.contains("完结")) {
                    novel.setStatus("完结");
                } else if (fullText.contains("连载")) {
                    novel.setStatus("连载中");
                }
                
                novel.setCategory(categoryName);
                novel.setRankPosition(position++);
                novel.setRankType("点击榜");
                
                if (novel.getTitle() != null && !novel.getTitle().isEmpty()) {
                    novels.add(novel);
                }
            } catch (Exception e) {
                log.warn("解析小说元素失败: {}", e.getMessage());
            }
        }
    } finally {
        driver.quit();
    }
    
    log.info("从页面 {} 解析到 {} 本小说", url, novels.size());
    return novels;
}
```

### 方案2：寻找七猫的 API 接口（更优雅）

通过浏览器开发者工具（F12 -> Network）抓包，找到七猫网站加载小说列表的真实 API 接口，直接调用 API。

#### 操作步骤：

1. 打开 Chrome 浏览器
2. 访问 https://www.qimao.com/shuku/a-1-8-a-a-a-a-click-1/
3. 按 F12 打开开发者工具
4. 切换到 Network 标签页
5. 刷新页面
6. 查找类型为 XHR 或 Fetch 的请求
7. 找到返回小说列表数据的接口（通常是 JSON 格式）
8. 复制该接口的 URL 和参数

如果找到了 API，可以直接用 OkHttp 请求 JSON 数据，比 Selenium 更高效。

### 方案3：临时禁用爬虫功能

如果暂时不需要爬虫功能，可以在配置表中关闭：

```sql
UPDATE qimao_scraper_config 
SET config_value = 'false' 
WHERE config_key = 'enable_auto_scrape';
```

## 推荐步骤

1. **短期方案**：先禁用自动爬取，手动录入一些测试数据
2. **长期方案**：
   - 优先尝试找 API 接口（最稳定）
   - 如果找不到 API，再用 Selenium（资源消耗大，但可行）

## 测试数据

如果只是为了测试前端功能，可以手动插入一些假数据：

```sql
INSERT INTO qimao_novels (novel_id, title, author, category, description, word_count, status, novel_url, rank_position, rank_type) VALUES
('test_001', '霸道总裁的小娇妻', '测试作者1', '总裁豪门', '一个关于爱情与成长的故事...', '80.5万字', '连载中', 'https://www.qimao.com/book/test_001', 1, '点击榜'),
('test_002', '重生之豪门千金', '测试作者2', '总裁豪门', '她重生归来，誓要改写人生...', '65.3万字', '完结', 'https://www.qimao.com/book/test_002', 2, '点击榜'),
('test_003', '冷情总裁的追妻之路', '测试作者3', '总裁豪门', '他是商界传奇，她是设计新星...', '120.8万字', '连载中', 'https://www.qimao.com/book/test_003', 3, '点击榜');
```
