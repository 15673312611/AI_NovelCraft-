package com.novel.agentic.controller;

import com.novel.agentic.service.graph.IGraphService;
import com.novel.agentic.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 图谱管理控制器
 */
@RestController
@RequestMapping("/agentic/graph")
@CrossOrigin(origins = "*")
public class GraphManagementController {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphManagementController.class);

    @Autowired(required = false)
    private IGraphService graphService;
    
    

    /**
     * 删除一个角色的最新状态记录
     */
    @DeleteMapping("/character-state")
    public Map<String, Object> deleteCharacterState(
            @RequestParam("novelId") Long novelId,
            @RequestParam("characterName") String characterName) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            graphService.deleteCharacterState(novelId, characterName);
            return CollectionUtils.mapOf("status", "success", "message", "角色状态已删除");
        } catch (Exception e) {
            logger.error("删除角色状态失败: novelId={}, characterName={}", novelId, characterName, e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 更新角色状态（位置 / 境界 / 存活状态 / 人物信息）
     */
    @PutMapping("/character-state")
    public Map<String, Object> updateCharacterState(@RequestBody Map<String, Object> request) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            Long novelId = ((Number) request.get("novelId")).longValue();
            String characterName = (String) request.get("name");
            String location = (String) request.get("location");
            String realm = (String) request.get("realm");
            String characterInfo = (String) request.get("characterInfo");

            Boolean alive = null;
            Object aliveObj = request.get("alive");
            if (aliveObj instanceof Boolean) {
                alive = (Boolean) aliveObj;
            }

            Number chapterNum = request.get("chapter") instanceof Number ? (Number) request.get("chapter") : null;
            Integer chapterNumber = chapterNum != null ? chapterNum.intValue() : 0;

            graphService.upsertCharacterStateWithInfo(novelId, characterName, location, realm, alive, characterInfo, chapterNumber);
            return CollectionUtils.mapOf("status", "success", "message", "角色状态已更新");
        } catch (Exception e) {
            logger.error("更新角色状态失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    
    /**
     * 获取小说的所有图谱数据
     */
    @GetMapping("/data/{novelId}")
    public Map<String, Object> getGraphData(@PathVariable Long novelId) {
        if (graphService == null) {
            return CollectionUtils.mapOf("error", "图谱服务未启用");
        }

        try {
            Map<String, Object> graphData = graphService.getAllGraphData(novelId);
            logger.info("📊 查询小说 {} 的图谱数据完成", novelId);
            logger.info("  - CharacterStates: {}", graphData.get("totalCharacterStates"));
            logger.info("  - OpenQuests: {}", graphData.get("totalOpenQuests"));
            logger.info("  - RelationshipStates: {}", graphData.get("totalRelationshipStates"));
            return CollectionUtils.mapOf(
                "status", "success",
                "data", graphData
            );
        } catch (Exception e) {
            logger.error("查询图谱数据失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 删除一条关系状态（CharacterState之间的摘要关系）
     */
    @DeleteMapping("/relationship-state")
    public Map<String, Object> deleteRelationshipState(
            @RequestParam("novelId") Long novelId,
            @RequestParam("a") String characterA,
            @RequestParam("b") String characterB) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            graphService.deleteRelationshipState(novelId, characterA, characterB);
            return CollectionUtils.mapOf("status", "success", "message", "关系已删除");
        } catch (Exception e) {
            logger.error("删除关系状态失败: novelId={}, a={}, b={}", novelId, characterA, characterB, e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 更新关系状态（关系类型 / 强度）
     */
    @PutMapping("/relationship-state")
    public Map<String, Object> updateRelationshipState(@RequestBody Map<String, Object> request) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            Long novelId = ((Number) request.get("novelId")).longValue();
            String characterA = (String) request.get("a");
            String characterB = (String) request.get("b");
            String type = (String) request.get("type");

            Number strengthNum = request.get("strength") instanceof Number ? (Number) request.get("strength") : null;
            Double strength = strengthNum != null ? strengthNum.doubleValue() : null;

            Number chapterNum = request.get("chapter") instanceof Number ? (Number) request.get("chapter") : null;
            Integer chapterNumber = chapterNum != null ? chapterNum.intValue() : 0;

            graphService.upsertRelationshipState(novelId, characterA, characterB, type, strength, chapterNumber);
            return CollectionUtils.mapOf("status", "success", "message", "关系已更新");
        } catch (Exception e) {
            logger.error("更新关系状态失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }
    
    /**
     * 删除一个开放任务
     */
    @DeleteMapping("/open-quest")
    public Map<String, Object> deleteOpenQuest(
            @RequestParam("novelId") Long novelId,
            @RequestParam("id") String questId) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            graphService.deleteOpenQuest(novelId, questId);
            return CollectionUtils.mapOf("status", "success", "message", "任务已删除");
        } catch (Exception e) {
            logger.error("删除任务失败: novelId={}, questId={}", novelId, questId, e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

    /**
     * 更新开放任务（描述 / 状态 / 截止章节）
     */
    @PutMapping("/open-quest")
    public Map<String, Object> updateOpenQuest(@RequestBody Map<String, Object> request) {
        if (graphService == null) {
            return CollectionUtils.mapOf("status", "error", "message", "图谱服务未启用");
        }
        try {
            Long novelId = ((Number) request.get("novelId")).longValue();
            String questId = (String) request.get("id");
            String description = (String) request.get("description");
            String status = (String) request.get("status");

            Number introducedNum = request.get("introduced") instanceof Number ? (Number) request.get("introduced") : null;
            Integer introducedChapter = introducedNum != null ? introducedNum.intValue() : null;

            Number dueNum = request.get("due") instanceof Number ? (Number) request.get("due") : null;
            Integer dueByChapter = dueNum != null ? dueNum.intValue() : null;

            Number lastNum = request.get("lastUpdated") instanceof Number ? (Number) request.get("lastUpdated") : null;
            Integer lastUpdatedChapter = lastNum != null ? lastNum.intValue() : null;

            graphService.upsertOpenQuest(novelId, questId, description, status, introducedChapter, dueByChapter, lastUpdatedChapter);
            return CollectionUtils.mapOf("status", "success", "message", "任务已更新");
        } catch (Exception e) {
            logger.error("更新任务失败", e);
            return CollectionUtils.mapOf("status", "error", "message", e.getMessage());
        }
    }

}
