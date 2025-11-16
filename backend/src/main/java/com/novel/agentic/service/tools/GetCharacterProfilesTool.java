package com.novel.agentic.service.tools;

import com.novel.agentic.model.GraphEntity;
import com.novel.agentic.model.ToolDefinition;
import com.novel.agentic.service.graph.IGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 获取角色档案工具
 * 
 * 从图谱中查询已保存的角色档案（CharacterProfile节点）
 */
@Component
public class GetCharacterProfilesTool implements Tool {
    
    private static final Logger logger = LoggerFactory.getLogger(GetCharacterProfilesTool.class);
    
    @Autowired(required = false)
    private IGraphService graphService;
    
    @Autowired
    private ToolRegistry registry;
    
    @PostConstruct
    public void init() {
        registry.register(this);
    }
    
    @Override
    public String getName() {
        return "getCharacterProfiles";
    }
    
    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        
        Map<String, Object> novelIdProp = new HashMap<>();
        novelIdProp.put("type", "integer");
        novelIdProp.put("description", "小说ID");
        
        Map<String, Object> limitProp = new HashMap<>();
        limitProp.put("type", "integer");
        limitProp.put("description", "最多返回多少个角色档案（默认10）");
        limitProp.put("default", 10);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put("novelId", novelIdProp);
        properties.put("limit", limitProp);
        
        params.put("properties", properties);
        params.put("required", Arrays.asList("novelId"));
        
        return ToolDefinition.builder()
            .name(getName())
            .description("获取已保存的角色档案列表（从图谱CharacterProfile节点查询）")
            .parameters(params)
            .returnExample("[{\"character_name\":\"林默\",\"role_position\":\"主角\"}]")
            .costEstimate(50)
            .required(false)
            .build();
    }
    
    @Override
    public Object execute(Map<String, Object> args) throws Exception {
        if (graphService == null) {
            logger.warn("⚠️ 图谱服务不可用，返回空列表");
            return Collections.emptyList();
        }
        
        Long novelId = ((Number) args.get("novelId")).longValue();
        Integer limit = args.get("limit") instanceof Number 
            ? ((Number) args.get("limit")).intValue() 
            : 10;
        
        try {
            // 调用图谱服务查询 CharacterProfile 节点
            List<GraphEntity> profiles = graphService.getCharacterProfiles(novelId, limit);
            
            if (profiles == null || profiles.isEmpty()) {
                logger.info("ℹ️ 未找到角色档案: novelId={}", novelId);
                return Collections.emptyList();
            }
            
            // 转换为 Map 列表
            List<Map<String, Object>> result = profiles.stream()
                .map(entity -> {
                    Map<String, Object> profile = new HashMap<>(entity.getProperties());
                    profile.putIfAbsent("id", entity.getId());
                    profile.putIfAbsent("type", entity.getType());
                    return profile;
                })
                .collect(Collectors.toList());
            
            logger.info("✅ 查询到{}个角色档案", result.size());
            return result;
            
        } catch (Exception e) {
            logger.error("❌ 查询角色档案失败: novelId={}", novelId, e);
            return Collections.emptyList();
        }
    }
}

