package com.novel.demo;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 提示词爬虫程序
 * 从 https://novel.apuxi.cn/prompts.php 爬取提示词数据并保存为文件
 */
public class PromptScraper {
    
    private static final String TARGET_URL = "https://novel.apuxi.cn/prompts.php";
    private static final String OUTPUT_DIR = "prompts_output";
    
    public static void main(String[] args) {
        try {
            System.out.println("开始爬取提示词数据...");
            System.out.println("目标URL: " + TARGET_URL);
            
            // 1. 发送HTTP请求获取页面内容
            String htmlContent = fetchWebPage(TARGET_URL);
            
            if (htmlContent == null || htmlContent.isEmpty()) {
                System.err.println("获取页面内容失败");
                return;
            }
            
            System.out.println("页面内容获取成功，长度: " + htmlContent.length() + " 字符");
            
            // 2. 从HTML中提取allPrompts数组的JSON数据
            String jsonData = extractPromptsJson(htmlContent);
            
            if (jsonData == null) {
                System.err.println("未能提取到提示词数据");
                System.err.println("请检查页面结构是否发生变化");
                return;
            }
            
            System.out.println("成功提取JSON数据");
            
            // 3. 解析JSON并保存为文件
            parseAndSavePrompts(jsonData);
            
            System.out.println("\n提示词爬取完成！");
            
        } catch (Exception e) {
            System.err.println("爬取过程出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 发送HTTP GET请求获取页面内容
     */
    private static String fetchWebPage(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            // 设置请求方法和头部
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setRequestProperty("Connection", "keep-alive");
            connection.setRequestProperty("Cookie", "PHPSESSID=lng3lv402sieg187qno059gtdh");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            System.out.println("HTTP响应码: " + responseCode);
            
            if (responseCode != 200) {
                System.err.println("HTTP请求失败，响应码: " + responseCode);
                return null;
            }
            
            // 读取响应内容
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            return content.toString();
            
        } finally {
            connection.disconnect();
        }
    }
    
    /**
     * 从HTML内容中提取allPrompts的JSON数据
     */
    private static String extractPromptsJson(String htmlContent) {
        // 使用正则表达式提取 let allPrompts = [...]; 中的数组部分
        // Pattern.DOTALL 使 . 能匹配换行符
        Pattern pattern = Pattern.compile("let\\s+allPrompts\\s*=\\s*(\\[.*?\\]);", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(htmlContent);
        
        if (matcher.find()) {
            String jsonData = matcher.group(1);
            System.out.println("提取到的JSON数据长度: " + jsonData.length() + " 字符");
            return jsonData;
        }
        
        System.err.println("未找到 allPrompts 数组");
        return null;
    }
    
    /**
     * 解析JSON数据并保存为文件
     */
    private static void parseAndSavePrompts(String jsonData) throws IOException {
        // 创建输出目录
        Path outputPath = Paths.get(OUTPUT_DIR);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            System.out.println("创建输出目录: " + outputPath.toAbsolutePath());
        } else {
            System.out.println("使用输出目录: " + outputPath.toAbsolutePath());
        }
        
        // 解析JSON数组
        Gson gson = new Gson();
        JsonArray prompts = gson.fromJson(jsonData, JsonArray.class);
        
        System.out.println("找到 " + prompts.size() + " 个提示词条目");
        
        int savedCount = 0;
        int skippedCount = 0;
        
        for (int i = 0; i < prompts.size(); i++) {
            JsonObject prompt = prompts.get(i).getAsJsonObject();
            
            // 获取各个字段
            String id = prompt.has("id") ? prompt.get("id").getAsString() : null;
            String name = prompt.has("name") ? prompt.get("name").getAsString() : null;
            String category = prompt.has("category") ? prompt.get("category").getAsString() : null;
            String version = prompt.has("version") ? prompt.get("version").getAsString() : null;
            String content = prompt.has("content") ? prompt.get("content").getAsString() : null;
            
            // 如果没有content，跳过
            if (content == null || content.isEmpty()) {
                String displayName = name != null ? name : ("ID:" + id);
                System.out.println("跳过 (无content): " + displayName);
                skippedCount++;
                continue;
            }
            
            // 如果是未解锁的提示词，跳过
            if (content.contains("该提示词尚未解锁，无法查看内容") || 
                content.contains("该提示词尚未解锁") ||
                content.contains("无法查看内容")) {
                String displayName = name != null ? name : ("ID:" + id);
                System.out.println("跳过 (未解锁): " + displayName);
                skippedCount++;
                continue;
            }
            
            // 如果没有name，使用默认名称
            if (name == null || name.isEmpty()) {
                name = "prompt_" + (id != null ? id : (i + 1));
            }
            
            // 清理文件名（移除非法字符）
            String fileName = sanitizeFileName(name) + ".txt";
            
            // 解码Unicode字符串（将 \u5267\u60c5 这样的格式转换为中文）
            String decodedContent = decodeUnicode(content);
            
            // 构建完整的文件内容（包含元数据）
            StringBuilder fileContent = new StringBuilder();
            fileContent.append(repeatChar('=', 60)).append("\n");
            fileContent.append("提示词名称: ").append(name).append("\n");
            if (category != null) {
                fileContent.append("分类: ").append(category).append("\n");
            }
            fileContent.append(repeatChar('=', 60)).append("\n\n");
            fileContent.append(decodedContent);
            
            // 保存文件 (Java 8 兼容方式)
            Path filePath = outputPath.resolve(fileName);
            Files.write(filePath, fileContent.toString().getBytes(StandardCharsets.UTF_8));
            
            System.out.println("已保存 [" + (savedCount + 1) + "]: " + fileName + 
                             " (分类: " + (category != null ? category : "无") + ")");
            savedCount++;
        }
        
        System.out.println("\n" + repeatChar('=', 60));
        System.out.println("统计信息:");
        System.out.println("  总条目数: " + prompts.size());
        System.out.println("  成功保存: " + savedCount + " 个文件");
        System.out.println("  跳过条目: " + skippedCount + " 个 (无content)");
        System.out.println(repeatChar('=', 60));
    }
    
    /**
     * 重复字符（Java 8 兼容方式）
     */
    private static String repeatChar(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
    
    /**
     * 清理文件名，移除非法字符
     */
    private static String sanitizeFileName(String fileName) {
        // 移除或替换Windows/Linux文件系统不允许的字符
        String cleaned = fileName.replaceAll("[\\\\/:*?\"<>|]", "_")
                                 .trim()
                                 .replaceAll("\\s+", "_");
        
        // 限制文件名长度（避免过长）
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
        }
        
        return cleaned;
    }
    
    /**
     * 将Unicode编码的字符串转换为中文
     * 例如: \u5267\u60c5\u89c4\u5212\u5e08 -> 剧情规划师
     */
    private static String decodeUnicode(String unicode) {
        if (unicode == null) {
            return null;
        }
        
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < unicode.length()) {
            char c = unicode.charAt(i);
            
            // 检查是否是Unicode转义序列 
            if (c == '\\' && i + 1 < unicode.length() && unicode.charAt(i + 1) == 'u') {
                // 确保有足够的字符来解析
                if (i + 5 < unicode.length()) {
                    String hex = unicode.substring(i + 2, i + 6);
                    try {
                        int charCode = Integer.parseInt(hex, 16);
                        result.append((char) charCode);
                        i += 6; // 跳过
                    } catch (NumberFormatException e) {
                        // 如果解析失败，保留原字符
                        result.append(c);
                        i++;
                    }
                } else {
                    result.append(c);
                    i++;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        
        return result.toString();
    }
}

