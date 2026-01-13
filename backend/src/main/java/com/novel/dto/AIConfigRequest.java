package com.novel.dto;

/**
 * AI配置请求DTO
 * 用于前端传递AI配置参数
 */
public class AIConfigRequest {
    
    /**
     * AI服务商类型：deepseek, qwen, kimi
     */
    private String provider;
    
    /**
     * API密钥
     */
    private String apiKey;
    
    /**
     * 模型名称
     */
    private String model;
    
    /**
     * API基础URL（可选，某些服务商需要）
     */
    private String baseUrl;
    
    /**
     * 温度参数（可选，0-2范围）
     */
    private Double temperature;

    // 默认构造函数
    public AIConfigRequest() {
    }

    // 全参数构造函数
    public AIConfigRequest(String provider, String apiKey, String model, String baseUrl) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
    }

    // Getters and Setters
    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    /**
     * 获取实际使用的baseUrl
     * 如果未指定则根据provider返回默认值
     */
    public String getEffectiveBaseUrl() {
        if (baseUrl != null && !baseUrl.trim().isEmpty()) {
            return baseUrl;
        }
        
        // 根据provider返回默认baseUrl
        if (provider != null) {
            switch (provider.toLowerCase()) {
                case "deepseek":
                    return "https://api.deepseek.com";
                case "qwen":
                case "tongyi":
                    return "https://dashscope.aliyuncs.com/compatible-mode/v1";
                case "kimi":
                    return "https://api.moonshot.cn";
                default:
                    return "https://api.openai.com";
            }
        }
        
        return "https://api.openai.com";
    }

    /**
     * 验证配置是否有效
     */
    public boolean isValid() {
        return apiKey != null && !apiKey.trim().isEmpty() 
            && model != null && !model.trim().isEmpty()
            && provider != null && !provider.trim().isEmpty();
    }
    
    /**
     * 获取完整的API URL
     * 智能处理不同服务商的URL格式
     */
    public String getApiUrl() {
        String base = getEffectiveBaseUrl();
        
        // 智能构建URL：如果baseUrl已经包含/v1，则只添加/chat/completions
        if (base.endsWith("/v1")) {
            return base + "/chat/completions";
        } else if (base.endsWith("/")) {
            return base + "v1/chat/completions";
        } else {
            return base + "/v1/chat/completions";
        }
    }
}
