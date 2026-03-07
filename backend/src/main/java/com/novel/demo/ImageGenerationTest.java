package com.novel.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 图片生成API测试
 * 可以直接运行main方法进行测试
 */
public class ImageGenerationTest {

    private static final String API_URL = "http://api.cutb.cn/v1/images/generations";
    private static final String API_KEY = "sk-jUSPTmh5C2PbGHb98cFcA72eFcA54b4aB2Cf21E33b9cA049";
    
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(360, TimeUnit.SECONDS)
            .writeTimeout(360, TimeUnit.SECONDS)
            .readTimeout(360, TimeUnit.SECONDS)
            .build();
    
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        
        try {
            System.out.println("=== 开始测试图片生成API ===");
            System.out.println("API地址: " + API_URL);
            System.out.println();
            
            // 构建请求体
            Map<String, Object> requestBody = buildRequestBody();
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            
            System.out.println("📝 请求体:");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(requestBody));
            System.out.println();
            
            // 发送请求
            String response = sendRequest(jsonBody);
            
            System.out.println("✅ 响应结果:");
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objectMapper.readValue(response, Object.class)));
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.out.println();
            System.out.println("⏱️  总耗时: " + duration + " 毫秒 (" + String.format("%.2f", duration / 1000.0) + " 秒)");
            
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            System.err.println("❌ 测试失败: " + e.getMessage());
            System.err.println("⏱️  失败前耗时: " + duration + " 毫秒 (" + String.format("%.2f", duration / 1000.0) + " 秒)");
            e.printStackTrace();
        }
    }

    /**
     * 构建请求体
     */
    private static Map<String, Object> buildRequestBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("aspect_ratio", "16:9");
        body.put("image", new String[]{
            "https://szridea.oss-cn-beijing.aliyuncs.com/sora-anime/reference-images/28cfd07e-a36f-497c-b872-c469ccbda989.jpg"
        });
        body.put("size", "1920x1080");
        body.put("model", "nano-banana-pro");
        body.put("prompt", "直接帮我生成3个人物 两女一男 好看点就行 全身 正面图片 " +
                "然后每个人物左上角写个名称 王杰 婉儿 杜琴 这三个 背景纯白 --ar 16:9, " +
                "masterpiece, best quality, ultra detailed, 8k, high resolution");
        
        return body;
    }

    /**
     * 发送HTTP请求
     */
    private static String sendRequest(String jsonBody) throws IOException {
        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json; charset=utf-8")
        );
        
        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();
        
        System.out.println("🚀 发送请求...");
        System.out.println("请求头: Authorization: Bearer " + API_KEY.substring(0, 20) + "...");
        System.out.println();
        
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败: HTTP " + response.code() + " - " + response.message());
            }
            
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("响应体为空");
            }
            
            return responseBody.string();
        }
    }
}
