package com.novel.dto;

/**
 * AI调用请求DTO
 * 用于替代前端传递的AI配置，改为使用后端统一配置
 */
public class AICallRequest {
    
    private String modelId;  // 可选，指定使用的模型ID，为空则使用默认模型
    private Integer maxTokens;  // 可选，最大输出token数
    private String taskDescription;  // 任务描述，用于记录消费
    
    public AICallRequest() {}
    
    public AICallRequest(String taskDescription) {
        this.taskDescription = taskDescription;
    }
    
    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }
    
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    
    public String getTaskDescription() { return taskDescription; }
    public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }
}
