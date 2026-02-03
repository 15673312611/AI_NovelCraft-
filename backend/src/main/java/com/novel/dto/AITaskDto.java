package com.novel.dto;

import com.novel.domain.entity.AITask;
import java.time.LocalDateTime;

public class AITaskDto {
    private Long id;
    private String title;
    private String description;
    private AITask.AITaskType type;
    private AITask.AITaskStatus status;
    private String prompt;
    private String result;
    private String parameters;
    private String errorMessage;
    private Long userId;
    private Long novelId;
    private String model;
    private Integer maxTokens;
    private Double temperature;
    private Integer retryCount;
    private Integer maxRetries;
    private Integer timeout;
    private Integer tokenCount;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Long executionTime;
    private Double cost;
    private Integer progress;
    private String novelTitle;
    private String createdByUsername;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public AITask.AITaskType getType() { return type; }
    public void setType(AITask.AITaskType type) { this.type = type; }
    public AITask.AITaskStatus getStatus() { return status; }
    public void setStatus(AITask.AITaskStatus status) { this.status = status; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getNovelId() { return novelId; }
    public void setNovelId(Long novelId) { this.novelId = novelId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }
    public Integer getTimeout() { return timeout; }
    public void setTimeout(Integer timeout) { this.timeout = timeout; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Long getExecutionTime() { return executionTime; }
    public void setExecutionTime(Long executionTime) { this.executionTime = executionTime; }
    public Double getCost() { return cost; }
    public void setCost(Double cost) { this.cost = cost; }
    public Integer getProgress() { return progress; }
    public void setProgress(Integer progress) { this.progress = progress; }
    public String getNovelTitle() { return novelTitle; }
    public void setNovelTitle(String novelTitle) { this.novelTitle = novelTitle; }
    public String getCreatedByUsername() { return createdByUsername; }
    public void setCreatedByUsername(String createdByUsername) { this.createdByUsername = createdByUsername; }

    public static AITaskDto fromEntity(AITask entity) {
        AITaskDto dto = new AITaskDto();
        dto.setId(entity.getId());
        dto.setTitle(entity.getName());
        dto.setDescription("");
        dto.setType(entity.getType());
        dto.setStatus(entity.getStatus());
        dto.setPrompt(entity.getInput());
        dto.setResult(entity.getOutput());
        dto.setParameters(entity.getParameters());
        dto.setErrorMessage(entity.getError());
        dto.setUserId(entity.getUserId());
        dto.setNovelId(entity.getNovelId());
        dto.setModel("gpt-4");
        dto.setMaxTokens(1000);
        dto.setTemperature(0.7);
        dto.setRetryCount(entity.getRetryCount());
        dto.setMaxRetries(entity.getMaxRetries());
        dto.setTimeout(300);
        dto.setTokenCount(0);
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setStartedAt(entity.getStartedAt());
        dto.setCompletedAt(entity.getCompletedAt());
        dto.setProgress(entity.getProgressPercentage());
        dto.setCost(entity.getActualCost());
        return dto;
    }
}

