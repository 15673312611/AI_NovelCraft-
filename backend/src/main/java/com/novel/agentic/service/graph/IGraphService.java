package com.novel.agentic.service.graph;

import com.novel.agentic.model.GraphEntity;

import java.util.List;
import java.util.Map;

/**
 * 图谱服务统一接口
 * 
 * 提供Neo4j和内存两种实现，运行时根据配置选择
 */
public interface IGraphService {
    
    /**
     * 查询相关事件
     * 
     * 策略：基于因果链、参与者、关系距离综合排序
     * 
     * @param novelId 小说ID
     * @param chapterNumber 当前章节号
     * @param limit 最大返回数量
     * @return 相关事件列表（按相关性排序）
     */
    List<GraphEntity> getRelevantEvents(Long novelId, Integer chapterNumber, Integer limit);
    
    /**
     * 查询未回收伏笔
     * 
     * 策略：按重要性、年龄排序
     * 
     * @param novelId 小说ID
     * @param chapterNumber 当前章节号
     * @param limit 最大返回数量
     * @return 未回收伏笔列表
     */
    List<GraphEntity> getUnresolvedForeshadows(Long novelId, Integer chapterNumber, Integer limit);
    
    /**
     * 查询情节线状态
     * 
     * 策略：检测久未推进、待发展的情节线
     * 
     * @param novelId 小说ID
     * @param chapterNumber 当前章节号
     * @param limit 最大返回数量
     * @return 情节线状态列表
     */
    List<GraphEntity> getPlotlineStatus(Long novelId, Integer chapterNumber, Integer limit);
    
    /**
     * 查询世界规则
     * 
     * 策略：按类别、重要性排序
     * 
     * @param novelId 小说ID
     * @param chapterNumber 当前章节号
     * @param limit 最大返回数量
     * @return 世界规则列表
     */
    List<GraphEntity> getWorldRules(Long novelId, Integer chapterNumber, Integer limit);
    
    /**
     * 查询角色关系网
     * 
     * 策略：查询指定角色的所有关系（对抗、合作、暧昧等）
     * 
     * @param novelId 小说ID
     * @param characterName 角色名称
     * @param limit 最大返回数量
     * @return 角色关系列表
     */
    List<GraphEntity> getCharacterRelationships(Long novelId, String characterName, Integer limit);
    
    /**
     * 按角色查询相关事件
     * 
     * 策略：查询角色参与的所有重要事件
     * 
     * @param novelId 小说ID
     * @param characterName 角色名称
     * @param chapterNumber 当前章节号
     * @param limit 最大返回数量
     * @return 事件列表
     */
    List<GraphEntity> getEventsByCharacter(Long novelId, String characterName, Integer chapterNumber, Integer limit);
    
    /**
     * 按因果链查询相关事件
     * 
     * 策略：从指定事件出发，沿因果链查询前因后果
     * 
     * @param novelId 小说ID
     * @param eventId 起始事件ID
     * @param depth 查询深度（几度关系）
     * @return 因果链事件列表
     */
    List<GraphEntity> getEventsByCausality(Long novelId, String eventId, Integer depth);
    
    /**
     * 查询冲突发展历史
     * 
     * 策略：查询主角与指定角色的所有对抗、冲突事件
     * 
     * @param novelId 小说ID
     * @param protagonistName 主角名称
     * @param antagonistName 对手名称
     * @param limit 最大返回数量
     * @return 冲突事件列表
     */
    List<GraphEntity> getConflictHistory(Long novelId, String protagonistName, String antagonistName, Integer limit);

    /**
     * 获取叙事节奏状态
     *
     * @param novelId 小说ID
     * @param chapterNumber 当前章节号
     * @param window 回溯窗口（最近多少章）
     * @return 包含最近节奏节点、统计指标与建议的Map
     */
    Map<String, Object> getNarrativeRhythmStatus(Long novelId, Integer chapterNumber, Integer window);

    /**
     * 查询活跃冲突弧线状态
     *
     * @param novelId 小说ID
     * @param chapterNumber 当前章节号
     * @param limit 最大返回数量
     * @return 冲突弧线列表
     */
    List<GraphEntity> getActiveConflictArcs(Long novelId, Integer chapterNumber, Integer limit);

    /**
     * 查询人物成长状态
     *
     * @param novelId 小说ID
     * @param chapterNumber 当前章节号
     * @param limit 最大返回数量
     * @return 人物成长节点列表
     */
    List<GraphEntity> getCharacterArcStatus(Long novelId, Integer chapterNumber, Integer limit);

    /**
     * 查询视角使用历史
     *
     * @param novelId 小说ID
     * @param chapterNumber 当前章节号
     * @param window 回溯窗口
     * @return 视角使用记录
     */
    List<GraphEntity> getPerspectiveHistory(Long novelId, Integer chapterNumber, Integer window);
    
    /**
     * 添加实体到图谱
     * 
     * @param novelId 小说ID
     * @param entity 实体对象
     */
    void addEntity(Long novelId, GraphEntity entity);
    
    /**
     * 批量添加实体
     * 
     * @param novelId 小说ID
     * @param entities 实体列表
     */
    void addEntities(Long novelId, List<GraphEntity> entities);
    
    /**
     * 添加关系到图谱
     * 
     * @param novelId 小说ID
     * @param fromEntityId 起始实体ID
     * @param relationshipType 关系类型（CAUSES, INVOLVES, TRIGGERS等）
     * @param toEntityId 目标实体ID
     * @param properties 关系属性
     */
    void addRelationship(Long novelId, String fromEntityId, String relationshipType, String toEntityId, Map<String, Object> properties);

    // 🆕 核心记忆账本写入（受控管道，不接受AI自由写入）
    void upsertCharacterState(Long novelId, String characterName, String location, String realm, Boolean alive, Integer chapterNumber);
    void upsertCharacterStateWithInfo(Long novelId, String characterName, String location, String realm, Boolean alive, String characterInfo, Integer chapterNumber);
    void upsertCharacterStateComplete(Long novelId, String characterName, Map<String, Object> stateData, Integer chapterNumber);
    void updateCharacterInventory(Long novelId, String characterName, List<String> items, Integer chapterNumber);
    void upsertRelationshipState(Long novelId, String characterA, String characterB, String type, Double strength, Integer chapterNumber);
    void upsertRelationshipStateComplete(Long novelId, String characterA, String characterB, Map<String, Object> relationData, Integer chapterNumber);
    void upsertOpenQuest(Long novelId, String questId, String description, String status, Integer introducedChapter, Integer dueByChapter, Integer lastUpdatedChapter);
    void resolveOpenQuest(Long novelId, String questId, Integer resolvedChapter);
    void addSummarySignals(Long novelId, Integer chapterNumber, Map<String, String> signals);

    void deleteRelationshipState(Long novelId, String characterA, String characterB);
    void deleteCharacterState(Long novelId, String characterName);
    void deleteOpenQuest(Long novelId, String questId);

    // 🆕 核心记忆账本查询（State Guard生成用）
    List<Map<String, Object>> getCharacterStates(Long novelId, Integer limit);
    List<Map<String, Object>> getTopRelationships(Long novelId, Integer limit);
    List<Map<String, Object>> getOpenQuests(Long novelId, Integer currentChapter);

    /**
     * 查询角色档案列表
     *
     * @param novelId 小说ID
     * @param limit 最大返回数量
     * @return 角色档案列表
     */
    List<GraphEntity> getCharacterProfiles(Long novelId, Integer limit);

    /**
     * 查询图谱统计信息
     *
     * @param novelId 小说ID
     * @return 统计信息（实体数量、关系数量等）
     */
    Map<String, Object> getGraphStatistics(Long novelId);
    
    /**
     * 检查服务可用性
     * 
     * @return true表示服务正常
     */
    boolean isAvailable();
    
    /**
     * 获取服务类型
     * 
     * @return NEO4J或MEMORY
     */
    String getServiceType();

    /**
     * 清空指定小说的图谱数据
     *
     * @param novelId 小说ID
     */
    void clearGraph(Long novelId);
    
    /**
     * 🆕 删除指定章节的所有图谱实体和关系
     * 
     * @param novelId 小说ID
     * @param chapterNumber 章节号
     */
    void deleteChapterEntities(Long novelId, Integer chapterNumber);
    
    /**
     * 获取小说的所有图谱数据
     * 
     * @param novelId 小说ID
     * @return 包含所有实体和关系的Map
     */
    Map<String, Object> getAllGraphData(Long novelId);
}

