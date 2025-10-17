package com.novel.controller;

import com.novel.common.Result;
import com.novel.dto.AIConfigRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 形容词挖掘接口：调用AI批量生成可疑形容词，并写入数据库
 */
@RestController
@RequestMapping("/ai-adjectives")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class AdjectiveMiningController {

    @Autowired
    private com.novel.service.AdjectiveMiningService miningService;

    /**
     * 触发挖掘（默认循环100次，每次100条），允许指定模型与类别
     * body: { aiConfig: {provider, apiKey, model, baseUrl}, loops?: number, batchSize?: number, category?: string, categories?: string[] }
     */
    @PostMapping("/mine")
    public Result<Map<String, Object>> mine(@RequestBody(required = false) Map<String, Object> body) {
        Integer loops = body != null && body.get("loops") != null ? Integer.valueOf(String.valueOf(body.get("loops"))) : 100;
        Integer batchSize = body != null && body.get("batchSize") != null ? Integer.valueOf(String.valueOf(body.get("batchSize"))) : 100;

        // 构建AI配置
        AIConfigRequest aiConfig = new AIConfigRequest();
        if (body != null && body.get("aiConfig") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> aiConfigMap = (Map<String, String>) body.get("aiConfig");
            aiConfig.setProvider(aiConfigMap.get("provider"));
            aiConfig.setApiKey(aiConfigMap.get("apiKey"));
            aiConfig.setModel(aiConfigMap.get("model"));
            aiConfig.setBaseUrl(aiConfigMap.get("baseUrl"));
        }
        
        if (!aiConfig.isValid()) {
            return Result.error("AI配置无效");
        }

        // 支持单一category或多category
        String category = body != null ? (String) body.get("category") : null;
        Object cats = body != null ? body.get("categories") : null;

        Map<String, Object> result = new java.util.HashMap<>();
        if (cats instanceof java.util.List) {
            for (Object c : (java.util.List<?>) cats) {
                String cat = String.valueOf(c);
                Map<String, Object> summary = miningService.mineTerms(cat, aiConfig, loops, batchSize);
                result.put(cat, summary);
            }
        } else if (category != null && !category.isEmpty()) {
            result.put(category, miningService.mineTerms(category, aiConfig, loops, batchSize));
        } else {
            // 兼容：默认只挖形容词
            result.put("adjective", miningService.mineAdjectives(aiConfig, loops, batchSize));
        }
        return Result.success(result);
    }

    /**
     * 批量导入词条到库
     * body: { category: string, lang?: string, words: string[] }
     */
    @PostMapping("/import")
    public Result<Map<String, Object>> importWords(@RequestBody Map<String, Object> body) {
        String category = body.get("category") != null ? String.valueOf(body.get("category")) : "adjective";
        String lang = body.get("lang") != null ? String.valueOf(body.get("lang")) : "zh-CN";
        java.util.List<String> words = new java.util.ArrayList<>();
        Object w = body.get("words");
        if (w instanceof java.util.List) {
            for (Object o : (java.util.List<?>) w) { if (o != null) words.add(String.valueOf(o)); }
        }
        Map<String, Object> summary = miningService.importTerms(category, lang, words);
        return Result.success(summary);
    }
}


