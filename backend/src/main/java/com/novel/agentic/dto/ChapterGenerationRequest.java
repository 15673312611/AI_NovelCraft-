package com.novel.agentic.dto;

import com.novel.dto.AIConfigRequest;
import java.util.Map;

/**
 * 章节生成请求DTO
 */
public class ChapterGenerationRequest {
    private Long novelId;
    private Integer startChapter;
    private Integer count;
    private String userAdjustment;
    private String stylePromptFile;
    private Long promptTemplateId;
    private Map<String, String> referenceContents;
    private AIConfigRequest aiConfig;

    public Long getNovelId() {
        return novelId;
    }

    public void setNovelId(Long novelId) {
        this.novelId = novelId;
    }

    public Integer getStartChapter() {
        return startChapter;
    }

    public void setStartChapter(Integer startChapter) {
        this.startChapter = startChapter;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getUserAdjustment() {
        return userAdjustment;
    }

    public void setUserAdjustment(String userAdjustment) {
        this.userAdjustment = userAdjustment;
    }

    public String getStylePromptFile() {
        return stylePromptFile;
    }

    public void setStylePromptFile(String stylePromptFile) {
        this.stylePromptFile = stylePromptFile;
    }

    public Long getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(Long promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }

    public Map<String, String> getReferenceContents() {
        return referenceContents;
    }

    public void setReferenceContents(Map<String, String> referenceContents) {
        this.referenceContents = referenceContents;
    }

    public AIConfigRequest getAiConfig() {
        return aiConfig;
    }

    public void setAiConfig(AIConfigRequest aiConfig) {
        this.aiConfig = aiConfig;
    }
}
