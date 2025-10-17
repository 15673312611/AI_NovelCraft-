package com.novel.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * AI任务实体类
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@TableName("ai_tasks")
public class AITask {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名称 */
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /** 任务类型，参见 AITaskType */
    @NotNull(message = "任务类型不能为空")
    private AITaskType type;

    /** 任务状态，默认 PENDING，参见 AITaskStatus */
    @NotNull(message = "任务状态不能为空")
    private AITaskStatus status = AITaskStatus.PENDING;

    /** 模型输入内容（通常为 JSON 或原始文本） */
    private String input;

    /** 模型输出内容（通常为文本或结构化 JSON） */
    private String output;

    /** 错误信息（失败时记录异常原因） */
    private String error;

    /**
     * 任务进度百分比，范围 0-100。
     * 由异步流程或业务更新，complete() 时置为 100。
     */
    @TableField("progress_percentage")
    private Integer progressPercentage = 0;

    /** 预计完成时间（用于进度/ETA 展示） */
    @TableField("estimated_completion")
    private LocalDateTime estimatedCompletion;

    /** 实际开始时间 */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /** 实际完成时间 */
    @TableField("completed_at")
    private LocalDateTime completedAt;

    /** 当前已重试次数 */
    @TableField("retry_count")
    private Integer retryCount = 0;

    /** 最大允许重试次数（包含首次以外的尝试） */
    @TableField("max_retries")
    private Integer maxRetries = 3;

    /** 任务运行所需的参数（JSON 字符串） */
    private String parameters;

    /** 预估成本（可选，用于预算/提示） */
    @TableField("cost_estimate")
    private Double costEstimate;

    /** 实际成本（可选，用于统计/结算） */
    @TableField("actual_cost")
    private Double actualCost;

    /** 所属用户ID（任务归属者） */
    @TableField("user_id")
    private Long userId;

    /** 创建人ID（操作者） */
    @TableField("created_by")
    private Long createdBy;

    /** 关联的小说ID（上下文绑定） */
    @TableField("novel_id")
    private Long novelId;

    /** 创建时间（由 MyBatis-Plus 自动填充） */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 最近更新时间（由 MyBatis-Plus 自动填充） */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // 构造函数
    /** 无参构造函数（持久化框架需要） */
    public AITask() {}

    /**
     * 便捷构造函数。
     *
     * @param name 任务名称
     * @param type 任务类型
     * @param input 输入内容
     * @param userId 所属用户ID
     * @param createdBy 创建人ID
     */
    public AITask(String name, AITaskType type, String input, Long userId, Long createdBy) {
        this.name = name;
        this.type = type;
        this.input = input;
        this.userId = userId;
        this.createdBy = createdBy;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AITaskType getType() {
        return type;
    }

    public void setType(AITaskType type) {
        this.type = type;
    }

    public AITaskStatus getStatus() {
        return status;
    }

    public void setStatus(AITaskStatus status) {
        this.status = status;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Integer getProgressPercentage() {
        return progressPercentage;
    }

    public void setProgressPercentage(Integer progressPercentage) {
        this.progressPercentage = progressPercentage;
    }

    public LocalDateTime getEstimatedCompletion() {
        return estimatedCompletion;
    }

    public void setEstimatedCompletion(LocalDateTime estimatedCompletion) {
        this.estimatedCompletion = estimatedCompletion;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public Double getCostEstimate() {
        return costEstimate;
    }

    public void setCostEstimate(Double costEstimate) {
        this.costEstimate = costEstimate;
    }

    public Double getActualCost() {
        return actualCost;
    }

    public void setActualCost(Double actualCost) {
        this.actualCost = actualCost;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Long getNovelId() {
        return novelId;
    }

    public void setNovelId(Long novelId) {
        this.novelId = novelId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // 业务方法
    /**
     * 标记任务开始执行：状态置为 RUNNING，并记录开始时间。
     */
    public void start() {
        this.status = AITaskStatus.RUNNING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 标记任务成功完成：状态置为 COMPLETED，保存输出并记录完成时间与 100% 进度。
     *
     * @param output 模型输出内容
     */
    public void complete(String output) {
        this.status = AITaskStatus.COMPLETED;
        this.output = output;
        this.completedAt = LocalDateTime.now();
        this.progressPercentage = 100;
    }

    /**
     * 标记任务失败：状态置为 FAILED，记录错误信息与完成时间。
     *
     * @param error 失败原因或异常信息
     */
    public void fail(String error) {
        this.status = AITaskStatus.FAILED;
        this.error = error;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 重试任务：当未超过最大重试次数时，重置状态与时间并递增重试计数。
     * 不会清空输入输出，仅清空错误与时间。
     */
    public void retry() {
        if (this.retryCount < this.maxRetries) {
            this.retryCount++;
            this.status = AITaskStatus.PENDING;
            this.error = null;
            this.startedAt = null;
            this.completedAt = null;
        }
    }

    /**
     * 是否允许继续重试。
     *
     * @return 当 retryCount < maxRetries 时返回 true
     */
    public boolean canRetry() {
        return this.retryCount < this.maxRetries;
    }

    /**
     * 是否已完成。
     */
    public boolean isCompleted() {
        return AITaskStatus.COMPLETED.equals(this.status);
    }

    /**
     * 是否失败。
     */
    public boolean isFailed() {
        return AITaskStatus.FAILED.equals(this.status);
    }

    /**
     * 是否运行中。
     */
    public boolean isRunning() {
        return AITaskStatus.RUNNING.equals(this.status);
    }

    // AI任务类型枚举
    /**
     * 任务类型枚举，描述不同的 AI 写作/处理场景。
     */
    public enum AITaskType {
        CHARACTER_CREATION("角色创建"),
        PLOT_GENERATION("情节生成"),
        DIALOGUE_WRITING("对话写作"),
        WORLD_BUILDING("世界观构建"),
        STORY_OUTLINE("故事大纲"),
        CHAPTER_WRITING("章节写作"),
        EDITING("内容编辑"),
        TRANSLATION("翻译"),
        ANALYSIS("内容分析");

        private final String description;

        /**
         * @param description 中文描述
         */
        AITaskType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    // AI任务状态枚举
    /**
     * 任务状态枚举，覆盖等待、运行、完成、失败、取消与暂停等生命周期。
     */
    public enum AITaskStatus {
        PENDING("等待中"),
        RUNNING("运行中"),
        COMPLETED("已完成"),
        FAILED("失败"),
        CANCELLED("已取消"),
        PAUSED("暂停");

        private final String description;

        /**
         * @param description 中文描述
         */
        AITaskStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        return "AITask{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", progressPercentage=" + progressPercentage +
                ", userId=" + userId +
                ", createdBy=" + createdBy +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AITask aiTask = (AITask) o;
        return id != null && id.equals(aiTask.getId());
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
} 