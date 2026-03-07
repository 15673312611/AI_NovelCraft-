package com.novel.script.dto;

import lombok.Data;

@Data
public class CreateVideoScriptRequest {
    private String title;
    private String idea;

    /** 每集目标时长（秒） */
    private Integer targetSeconds;

    /** 每集分镜数量（可选，不填则自动估算） */
    private Integer sceneCount;

    /** 计划集数 */
    private Integer episodeCount;

    /** 是否允许生成过程中根据剧情更新大纲 */
    private Boolean enableOutlineUpdate;

    /** 审稿最低通过分（1-10） */
    private Integer minPassScore;

    /** HALF_NARRATION / PURE_NARRATION */
    private String mode;

    /**
     * 剧本格式（影响每集正文输出结构）
     * SCENE：集-场台本（真人短剧/影视）
     * STORYBOARD：分镜脚本（漫剧/动态漫）
     */
    private String scriptFormat;

    /** AI模型ID（可选） */
    private String modelId;
}
