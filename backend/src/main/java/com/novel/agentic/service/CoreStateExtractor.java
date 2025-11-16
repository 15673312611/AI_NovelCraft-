package com.novel.agentic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.service.graph.IGraphService;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 核心状态抽取器（轻量、可靠优先）
 *
 * 只抽取：主角+Top3配角状态 + 本章任务推进标记
 * 避免复杂因果/关系网的抽取错误
 */
@Service
public class CoreStateExtractor {
    private static final Logger logger = LoggerFactory.getLogger(CoreStateExtractor.class);

    private final AIWritingService aiWritingService;
    private final IGraphService graphService;
    private final ObjectMapper objectMapper;

    public CoreStateExtractor(AIWritingService aiWritingService,
                              @Autowired(required = false) IGraphService graphService,
                              ObjectMapper objectMapper) {
        this.aiWritingService = aiWritingService;
        this.graphService = graphService;
        this.objectMapper = objectMapper;

        if (graphService == null) {
            logger.warn("⚠️⚠️⚠️ CoreStateExtractor: IGraphService未注入，实体抽取将被禁用 ⚠️⚠️⚠️");
            logger.warn("   原因：Neo4j未启动或连接失败");
            logger.warn("   影响：章节生成正常，但图谱数据不会保存");
            logger.warn("   解决：启动Neo4j服务（docker-compose up neo4j）或检查配置");
        } else {
            logger.info("✅ CoreStateExtractor初始化成功，图谱服务类型: {}", graphService.getServiceType());
        }
    }
    
    /**
     * 从章节内容抽取核心状态并入库（带冲突检测）
     */
    public void extractAndSaveCoreState(Long novelId, Integer chapterNumber,
                                       String chapterContent, String chapterTitle,
                                       AIConfigRequest aiConfig) {
        // 🔒 前置检查：图谱服务不可用时直接返回
        if (graphService == null) {
            logger.warn("⚠️ 图谱服务不可用，跳过核心状态抽取（novelId={}, chapter={}）", novelId, chapterNumber);
            return;
        }

        try {
            logger.info("🔍 开始抽取核心状态: novelId={}, chapter={}", novelId, chapterNumber);
            logger.info("   章节标题: {}", chapterTitle);
            logger.info("   章节内容长度: {} 字", chapterContent != null ? chapterContent.length() : 0);

            // 1. 调用AI抽取轻量JSON
            String extractedJson = callAIForExtraction(chapterContent, chapterTitle, chapterNumber, aiConfig);
            if (extractedJson == null || extractedJson.trim().isEmpty()) {
                logger.warn("❌ AI抽取返回空，跳过状态更新");
                return;
            }

            logger.info("✅ AI抽取成功，JSON长度: {} 字", extractedJson.length());
            logger.info("📄 抽取的JSON内容:\n{}", extractedJson);

            // 2. 解析JSON
            JsonNode root = objectMapper.readTree(extractedJson);
            logger.info("✅ JSON解析成功");

            // 3. 冲突检测（location/realm变化合理性）
            List<String> conflicts = detectConflicts(novelId, chapterNumber, root);
            if (!conflicts.isEmpty()) {
                logger.warn("⚠️ 检测到状态冲突，但仍继续入库（冲突：{}）", String.join("; ", conflicts));
                // 未来可选：触发用户确认或AI补写桥段
            }

            // 4. 保存主角状态
            logger.info("💾 开始保存主角状态...");
            JsonNode protagonist = root.path("protagonist");
            saveProtagonistState(novelId, chapterNumber, protagonist);

            // 5. 保存关键配角状态（Top3）+ 关系
            logger.info("💾 开始保存关键配角状态...");
            String protagonistName = protagonist.path("name").asText("");
            saveKeyCharactersState(novelId, chapterNumber, root.path("keyCharacters"), protagonistName);

            // 6. 更新任务推进（自动创建或更新OpenQuest）
            logger.info("💾 开始更新任务推进...");
            updateQuestProgress(novelId, chapterNumber, root.path("questProgress"));

            logger.info("✅ 核心状态抽取完成: novelId={}, chapter={}", novelId, chapterNumber);

        } catch (Exception e) {
            logger.error("❌ 核心状态抽取失败: novelId={}, chapter={}", novelId, chapterNumber, e);
            // 不抛异常，避免阻塞章节保存
        }
    }
    
    /**
     * 调用AI抽取轻量JSON（主角+Top3配角+任务）
     */
    private String callAIForExtraction(String content, String title, Integer chapterNumber, AIConfigRequest aiConfig) throws Exception {
        String prompt = buildExtractionPrompt(content, title, chapterNumber);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
            "你是信息抽取器。严格返回JSON，不含任何解释或markdown标记。"));
        messages.add(Map.of("role", "user", "content", prompt));

        String result = aiWritingService.generateContentWithMessages(messages, "core_state_extraction", aiConfig);

        // 清理可能的markdown代码块标记
        if (result != null) {
            result = result.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }

        return result;
    }
    
    /**
     * 构建抽取提示词（极简、只抽主角+Top3+任务）
     */
    private String buildExtractionPrompt(String content, String title, Integer chapterNumber) {
        return "从本章抽取核心状态信息，输出严格JSON（无多余文字）：\n\n" +
            "{\n" +
            "  \"protagonist\": {\n" +
            "    \"name\": \"主角名\",\n" +
            "    \"location\": \"当前所在地（精确到具体地点）\",\n" +
            "    \"realm\": \"当前境界/实力（如有变化必须标注）\",\n" +
            "    \"inventory\": [\"关键物品1\", \"关键物品2\"],\n" +
            "    \"alive\": true\n" +
            "  },\n" +
            "  \"keyCharacters\": [\n" +
            "    {\"name\": \"配角名\", \"location\": \"所在地\", \"relation\": \"与主角关系（敌对/互援/跟踪等）\"}\n" +
            "  ],\n" +
            "  \"questProgress\": {\n" +
            "    \"任务简称\": \"触发线索/推进/受阻/完成\"\n" +
            "  }\n" +
            "}\n\n" +
            "要求：\n" +
            "- keyCharacters只保留本章出现的Top3重要配角（次要路人不要）\n" +
            "- inventory只记录\"关键物品\"（武器/宝物/线索物），不记录普通消耗品\n" +
            "- questProgress只记录\"长期任务\"的推进（如\"收集材料\"\"寻找仇人\"），不记录琐事\n" +
            "- location必须具体（\"南疆黑市\"而非\"南疆\"；\"瘴海边缘\"而非\"野外\"）\n" +
            "- 如果本章无关键配角或任务推进，对应字段可为空数组/空对象\n\n" +
            "---\n" +
            "章节标题：" + title + "\n" +
            "章节号：第" + chapterNumber + "章\n" +
            "章节内容：\n" +
            content + "\n" +
            "---\n" +
            "请输出JSON：";
    }
    
    /**
     * 冲突检测（location/realm不合理变化）
     */
    private List<String> detectConflicts(Long novelId, Integer chapterNumber, JsonNode root) {
        List<String> conflicts = new ArrayList<>();
        
        if (chapterNumber <= 1) {
            return conflicts; // 第一章无需检测
        }
        
        try {
            JsonNode protagonist = root.path("protagonist");
            String name = protagonist.path("name").asText("");
            String newLoc = protagonist.path("location").asText("");
            String newRealm = protagonist.path("realm").asText("");
            
            if (name.isEmpty()) {
                return conflicts;
            }
            
            // 查询上一章状态（从图谱CharacterState）
            // TODO: 需要在IGraphService增加 getCharacterState 查询方法
            // 暂时记录到日志，未来可触发用户确认
            
            logger.debug("🔍 冲突检测: {}章 {}@{} realm={}", chapterNumber, name, newLoc, newRealm);
            
        } catch (Exception e) {
            logger.warn("冲突检测失败（忽略）: {}", e.getMessage());
        }
        
        return conflicts;
    }
    
    /**
     * 保存主角状态（包括inventory）
     */
    private void saveProtagonistState(Long novelId, Integer chapterNumber, JsonNode protagonist) {
        if (protagonist.isMissingNode() || protagonist.isNull()) {
            logger.warn("⚠️ protagonist节点缺失或为null，跳过");
            return;
        }

        String name = protagonist.path("name").asText("");
        if (name.isEmpty()) {
            logger.warn("⚠️ 主角名为空，跳过状态保存");
            return;
        }

        String location = protagonist.path("location").asText("");
        String realm = protagonist.path("realm").asText("");
        boolean alive = protagonist.path("alive").asBoolean(true);

        logger.info("📝 准备保存主角状态: name={}, location={}, realm={}, alive={}", name, location, realm, alive);

        // 保存到CharacterState
        graphService.upsertCharacterState(novelId, name, location, realm, alive, chapterNumber);
        logger.info("✅ 主角状态已调用upsertCharacterState");

        // 🆕 保存inventory（关键物品清单）
        JsonNode inventoryNode = protagonist.path("inventory");
        if (!inventoryNode.isMissingNode() && inventoryNode.isArray()) {
            java.util.List<String> items = new java.util.ArrayList<>();
            for (JsonNode item : inventoryNode) {
                String itemName = item.asText("");
                if (!itemName.isEmpty()) {
                    items.add(itemName);
                }
            }
            if (!items.isEmpty()) {
                logger.info("📝 准备保存主角inventory: {} 件物品", items.size());
                graphService.updateCharacterInventory(novelId, name, items, chapterNumber);
                logger.info("✅ 主角inventory已调用updateCharacterInventory");
            }
        } else {
            logger.info("ℹ️ 主角无inventory或inventory为空");
        }
    }
    
    /**
     * 保存关键配角状态（Top3）+ 关系
     */
    private void saveKeyCharactersState(Long novelId, Integer chapterNumber, JsonNode keyCharacters, String protagonistName) {
        if (keyCharacters.isMissingNode() || !keyCharacters.isArray()) {
            logger.info("ℹ️ keyCharacters节点缺失或非数组，跳过");
            return;
        }

        logger.info("📝 keyCharacters数组长度: {}", keyCharacters.size());

        int count = 0;
        for (JsonNode character : keyCharacters) {
            if (count >= 3) break; // 只保存Top3

            String name = character.path("name").asText("");
            if (name.isEmpty()) {
                logger.warn("⚠️ 配角{}名称为空，跳过", count);
                continue;
            }

            String location = character.path("location").asText("");
            String relation = character.path("relation").asText("");

            logger.info("📝 准备保存配角{}: name={}, location={}, relation={}", count+1, name, location, relation);

            // 保存状态
            graphService.upsertCharacterState(novelId, name, location, "", true, chapterNumber);
            logger.info("✅ 配角{}状态已调用upsertCharacterState", name);

            // 保存关系（如果有关系信息且主角名不为空）
            if (!relation.isEmpty() && !protagonistName.isEmpty()) {
                // 根据关系类型设置强度
                double strength = calculateRelationshipStrength(relation);

                logger.info("📝 准备保存关系: {} <-[{}]-> {}, 强度={}", protagonistName, relation, name, strength);
                graphService.upsertRelationshipState(novelId, protagonistName, name, relation, strength, chapterNumber);
                logger.info("✅ 关系已保存: {} <-> {}", protagonistName, name);
            } else if (!relation.isEmpty()) {
                logger.warn("⚠️ 配角{}有关系信息，但主角名为空，跳过关系保存", name);
            }

            count++;
        }

        logger.info("✅ 已保存{}个关键配角状态", count);
    }

    /**
     * 根据关系类型计算强度
     */
    private double calculateRelationshipStrength(String relationType) {
        if (relationType == null || relationType.isEmpty()) {
            return 0.5;
        }

        String type = relationType.toLowerCase();

        // 强关系（敌对、盟友、亲密）
        if (type.contains("敌对") || type.contains("仇恨") || type.contains("敌人")) {
            return 0.9;
        }
        if (type.contains("盟友") || type.contains("互援") || type.contains("合作")) {
            return 0.8;
        }
        if (type.contains("亲密") || type.contains("恋人") || type.contains("挚友")) {
            return 0.95;
        }

        // 中等关系
        if (type.contains("朋友") || type.contains("友好")) {
            return 0.6;
        }
        if (type.contains("竞争") || type.contains("对立")) {
            return 0.7;
        }

        // 弱关系
        if (type.contains("陌生") || type.contains("路人")) {
            return 0.2;
        }
        if (type.contains("认识") || type.contains("熟人")) {
            return 0.4;
        }

        // 默认中等强度
        return 0.5;
    }
    
    /**
     * 更新任务推进（自动创建或更新OpenQuest）
     */
    private void updateQuestProgress(Long novelId, Integer chapterNumber, JsonNode questProgress) {
        if (questProgress.isMissingNode() || questProgress.isNull() || !questProgress.isObject()) {
            logger.info("ℹ️ questProgress节点缺失或非对象，跳过");
            return;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = questProgress.fields();
        int count = 0;

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String questName = entry.getKey();
            String progress = entry.getValue().asText("");

            if (questName.isEmpty() || progress.isEmpty()) {
                logger.warn("⚠️ 任务{}名称或进度为空，跳过", count);
                continue;
            }

            // 生成questId（简化：用questName作为ID）
            String questId = "Q-" + questName.replaceAll("[\\s\\-]+", "_");

            logger.info("📝 准备更新任务: questId={}, questName={}, progress={}", questId, questName, progress);

            // 根据progress判断状态
            String status = "OPEN";
            if (progress.contains("完成") || progress.contains("解决")) {
                status = "RESOLVED";
                logger.info("📝 任务{}标记为已完成，调用resolveOpenQuest", questId);
                graphService.resolveOpenQuest(novelId, questId, chapterNumber);
            } else {
                // 自动设定due窗口：触发/推进后5章内需闭环；受阻后10章
                int dueWindow = progress.contains("受阻") ? 10 : 5;
                logger.info("📝 任务{}标记为进行中，due窗口={}章，调用upsertOpenQuest", questId, dueWindow);
                graphService.upsertOpenQuest(
                    novelId, questId, questName, status,
                    chapterNumber, chapterNumber + dueWindow, chapterNumber
                );
            }

            logger.info("✅ 任务{}已调用图谱服务", questId);
            count++;
        }

        logger.info("✅ 已更新{}个任务状态", count);
    }
}

