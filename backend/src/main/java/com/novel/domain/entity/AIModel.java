package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_model")
public class AIModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank
    @TableField("model_id")
    private String modelId;

    @NotBlank
    @TableField("display_name")
    private String displayName;

    private String provider = "OpenAI-Compatible";

    @TableField("api_base_url")
    private String apiBaseUrl;

    @TableField("api_key_ref")
    private String apiKeyRef;

    @TableField("max_tokens")
    private Integer maxTokens = 8192;

    @TableField("cost_per_1k")
    private Double costPer1k = 0.0;

    @TableField("input_price_per_1k")
    private BigDecimal inputPricePer1k = BigDecimal.ZERO;

    @TableField("output_price_per_1k")
    private BigDecimal outputPricePer1k = BigDecimal.ZERO;

    @TableField("cost_multiplier")
    private BigDecimal costMultiplier = BigDecimal.ONE;

    @TableField("temperature")
    private BigDecimal temperature = BigDecimal.ONE;

    private Boolean available = true;

    @TableField("is_default")
    private Boolean isDefault = false;

    @TableField("sort_order")
    private Integer sortOrder = 0;

    private String description;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }

    public String getApiKeyRef() { return apiKeyRef; }
    public void setApiKeyRef(String apiKeyRef) { this.apiKeyRef = apiKeyRef; }

    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Double getCostPer1k() { return costPer1k; }
    public void setCostPer1k(Double costPer1k) { this.costPer1k = costPer1k; }

    public BigDecimal getInputPricePer1k() { return inputPricePer1k; }
    public void setInputPricePer1k(BigDecimal inputPricePer1k) { this.inputPricePer1k = inputPricePer1k; }

    public BigDecimal getOutputPricePer1k() { return outputPricePer1k; }
    public void setOutputPricePer1k(BigDecimal outputPricePer1k) { this.outputPricePer1k = outputPricePer1k; }

    public BigDecimal getCostMultiplier() { return costMultiplier; }
    public void setCostMultiplier(BigDecimal costMultiplier) { this.costMultiplier = costMultiplier; }

    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }

    public Boolean getAvailable() { return available; }
    public void setAvailable(Boolean available) { this.available = available; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

