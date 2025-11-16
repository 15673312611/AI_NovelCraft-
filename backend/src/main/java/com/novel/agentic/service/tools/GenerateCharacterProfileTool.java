package com.novel.agentic.service.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.agentic.model.GraphEntity;
import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.PromptTemplateService;
import com.novel.agentic.service.graph.IGraphService;
import com.novel.service.AIWritingService;
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
    private static final String PROMPT_FILE = "character_profile.txt";

    @Autowired
    private ToolRegistry registry;

    @Autowired
    private PromptTemplateService promptTemplateService;

    @Autowired
    private AIWritingService aiWritingService;

    @Autowired
    private IGraphService graphService;

    @Autowired
    private ObjectMapper objectMapper;

    private String basePrompt;

    @PostConstruct
    public void init() {
        registry.register(this);
        basePrompt = promptTemplateService.loadTemplate(PROMPT_FILE);
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
        chapterNumber.put("description", "当前章节号（可选）");

        Map<String, Object> characterName = new HashMap<>();
        characterName.put("type", "string");
        characterName.put("description", "角色名称");

        Map<String, Object> roleHint = new HashMap<>();
        roleHint.put("type", "string");
        roleHint.put("description", "角色定位/剧情作用（可选）");

        Map<String, Object> contextHint = new HashMap<>();
        contextHint.put("type", "string");
        contextHint.put("description", "最新剧情背景，帮助生成具体档案（可选）");

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
            .description("生成或更新角色档案（十维档案），帮助AI掌握角色设定、目标、关系。返回JSON并自动写入图谱，供后续章节复用。")
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
        String response = aiWritingService.generateContent(prompt, "character_profile");

        Map<String, Object> profile = parseProfile(response);
        profile.putIfAbsent("character_name", characterName);
        if (role != null) {
            profile.put("requested_role", role);
        }

        persistProfile(novelId, chapterNumber, profile);

        return profile;
    }

    private String buildPrompt(String name, String role, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append(basePrompt).append("\n\n");
        sb.append("【角色信息】\n");
        sb.append("- 角色名称：").append(name).append("\n");
        if (StringUtils.isNotBlank(role)) {
            sb.append("- 角色定位：").append(role).append("\n");
        }
        if (StringUtils.isNotBlank(context)) {
            sb.append("- 剧情背景：").append(context).append("\n");
        }
        sb.append("\n请严格按照上述要求输出 JSON：");
        return sb.toString();
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
            logger.info("✅ 角色档案写入图谱: novelId={}, character={}", novelId, characterName);
        } catch (Exception e) {
            logger.error("角色档案写入图谱失败: novelId={}", novelId, e);
        }
    }
}

