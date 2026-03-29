package com.novel.agentic.service.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.GraphEntity;
import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import com.novel.dto.AIConfigRequest;
import com.novel.service.AIWritingService;
import com.novel.service.prompt.ManagedPromptService;
import com.novel.service.prompt.SystemPromptDescriptor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
public class GenerateCharacterProfileTool implements Tool {

    private static final Logger logger = LoggerFactory.getLogger(GenerateCharacterProfileTool.class);
    private static final String PROMPT_KEY = "agentic.tool.character_profile";
    private static final String DEFAULT_PROMPT = """
        你是一名长篇网文角色设计助手。
        请基于角色姓名、定位和剧情背景，输出可直接用于后续创作的十维角色档案。

        输出要求：
        1. 只输出合法 JSON 对象，不要输出解释、标题或 Markdown。
        2. 字段名必须使用英文 snake_case。
        3. 所有核心字段都必须填写；如信息不足，请给出克制且符合剧情逻辑的补全。
        4. 档案必须服务剧情推进，避免空泛套话。

        必须包含以下结构：
        {
          "character_name": "",
          "role_position": "",
          "core_profile": {
            "sex": "",
            "age": 0,
            "identity": "",
            "background": "",
            "core_tags": []
          },
          "memory_hooks": {
            "appearance": "",
            "signature_quote": "",
            "signature_action": ""
          },
          "contrast_points": {
            "before": "",
            "after": ""
          },
          "extreme_trait": "",
          "spiritual_core": "",
          "ultimate_goals": [
            {
              "stage": "",
              "goal": "",
              "related_conflict": ""
            }
          ],
          "relationships": [
            {
              "target": "",
              "surface_relation": "",
              "real_attitude": "",
              "evolution_hint": ""
            }
          ],
          "scene_guidance": {
            "daily": "",
            "conflict": "",
            "growth": ""
          }
        }

        额外要求：
        - ultimate_goals 至少 2 项。
        - relationships 至少 2 项。
        - 输出内容要与人物定位、上下文冲突和后续成长空间一致。
        """;

    @Autowired
    private ToolRegistry registry;

    @Autowired
    private ManagedPromptService managedPromptService;

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private IGraphService graphService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getName() {
        return "generateCharacterProfile";
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");

        Map<String, Object> novelId = new HashMap<>();
        novelId.put("type", "integer");
        novelId.put("description", "小说ID");

        Map<String, Object> chapterNumber = new HashMap<>();
        chapterNumber.put("type", "integer");
        chapterNumber.put("description", "当前章节号，可选");

        Map<String, Object> characterName = new HashMap<>();
        characterName.put("type", "string");
        characterName.put("description", "角色名称");

        Map<String, Object> roleHint = new HashMap<>();
        roleHint.put("type", "string");
        roleHint.put("description", "角色定位或剧情作用，可选");

        Map<String, Object> contextHint = new HashMap<>();
        contextHint.put("type", "string");
        contextHint.put("description", "最新剧情背景，帮助生成更具体的人设档案，可选");

        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelId);
        properties.put("chapterNumber", chapterNumber);
        properties.put("characterName", characterName);
        properties.put("role", roleHint);
        properties.put("context", contextHint);

        params.put("properties", properties);
        params.put("required", new String[]{"novelId", "characterName"});

        return ToolDefinition.builder()
            .name(getName())
            .description("生成或更新角色十维档案，返回 JSON 并自动写入图谱供后续复用。")
            .parameters(params)
            .returnExample("{\"character_name\":\"林默\",\"role_position\":\"潜伏线核心\"}")
            .costEstimate(1200)
            .required(false)
            .build();
    }

    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        Long novelId = ((Number) args.get("novelId")).longValue();
        String characterName = (String) args.get("characterName");
        if (StringUtils.isBlank(characterName)) {
            throw new IllegalArgumentException("characterName 不能为空");
        }

        Integer chapterNumber = args.get("chapterNumber") instanceof Number ? ((Number) args.get("chapterNumber")).intValue() : null;
        String role = args.get("role") instanceof String ? (String) args.get("role") : null;
        String context = args.get("context") instanceof String ? (String) args.get("context") : null;

        String prompt = buildPrompt(characterName, role, context);
        AIConfigRequest aiConfig = args.get("__aiConfig") instanceof AIConfigRequest
            ? (AIConfigRequest) args.get("__aiConfig") : null;
        String response = aiWritingService.generateContent(prompt, "character_profile", aiConfig);

        Map<String, Object> profile = parseProfile(response);
        profile.putIfAbsent("character_name", characterName.trim());
        if (StringUtils.isNotBlank(role)) {
            profile.put("requested_role", role.trim());
        }

        persistProfile(novelId, chapterNumber, profile);
        return profile;
    }

    private String buildPrompt(String name, String role, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append(resolveBasePrompt()).append("\n\n");
        sb.append("【角色信息】\n");
        sb.append("- 角色名称：").append(name.trim()).append("\n");
        if (StringUtils.isNotBlank(role)) {
            sb.append("- 角色定位：").append(role.trim()).append("\n");
        }
        if (StringUtils.isNotBlank(context)) {
            sb.append("- 剧情背景：").append(context.trim()).append("\n");
        }
        sb.append("\n请严格按照上述要求输出 JSON。");
        return sb.toString();
    }

    private String resolveBasePrompt() {
        String prompt = managedPromptService.getContent(SystemPromptDescriptor.builder()
            .promptKey(PROMPT_KEY)
            .name("十维角色档案提示词")
            .category("agentic")
            .description("用于生成角色十维档案并写入图谱的基础提示词")
            .sortOrder(532)
            .defaultContent(DEFAULT_PROMPT)
            .build());
        if (StringUtils.isBlank(prompt)) {
            throw new IllegalStateException("Missing prompt template: " + PROMPT_KEY);
        }
        return prompt;
    }

    private Map<String, Object> parseProfile(String response) throws Exception {
        String json = response.trim();
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    private void persistProfile(Long novelId, Integer chapterNumber, Map<String, Object> profile) {
        try {
            String characterName = profile.getOrDefault("character_name", profile.getOrDefault("name", "character")).toString();
            String sanitizedName = characterName.replaceAll("\\s+", "_").toLowerCase();
            String id = "CharacterProfile:" + sanitizedName;

            GraphEntity entity = GraphEntity.builder()
                .type("CharacterProfile")
                .id(id)
                .chapterNumber(chapterNumber)
                .properties(profile)
                .source("agentic_profile")
                .build();

            graphService.addEntity(novelId, entity);
            logger.info("角色档案已写入图谱: novelId={}, character={}", novelId, characterName);
        } catch (Exception e) {
            logger.error("角色档案写入图谱失败: novelId={}", novelId, e);
        }
    }
}
