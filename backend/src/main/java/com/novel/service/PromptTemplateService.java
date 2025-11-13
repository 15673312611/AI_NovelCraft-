package com.novel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novel.domain.entity.PromptTemplate;
import com.novel.domain.entity.PromptTemplateFavorite;
import com.novel.repository.PromptTemplateRepository;
import com.novel.repository.PromptTemplateFavoriteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 提示词模板服务
 */
@Service
public class PromptTemplateService extends ServiceImpl<PromptTemplateRepository, PromptTemplate> {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateService.class);

    @Autowired
    private PromptTemplateFavoriteRepository favoriteRepository;

    // 支持的占位符列表
    private static final Set<String> SUPPORTED_PLACEHOLDERS = new HashSet<>(Arrays.asList(
        "{title}",           // 小说标题
        "{genre}",           // 小说类型
        "{chapters}",        // 目标章数
        "{words}",           // 目标字数
        "{idea}",            // 用户核心构思（必填）
        "{outlineWordLimit}" // 大纲字数限制
    ));

    // 必填占位符列表
    private static final Set<String> REQUIRED_PLACEHOLDERS = new HashSet<>(Arrays.asList(
        "{idea}"  // 用户构思是必须的
    ));

    // 占位符说明
    private static final Map<String, String> PLACEHOLDER_DESCRIPTIONS;
    static {
        Map<String, String> map = new HashMap<>();
        map.put("{title}", "小说标题");
        map.put("{genre}", "小说类型");
        map.put("{chapters}", "目标章数");
        map.put("{words}", "目标字数");
        map.put("{idea}", "用户核心构思（必填）");
        map.put("{outlineWordLimit}", "大纲字数限制");
        PLACEHOLDER_DESCRIPTIONS = Collections.unmodifiableMap(map);
    }

    /**
     * 获取所有可用的模板（官方+用户自定义）
     */
    public List<PromptTemplate> getAvailableTemplates(Long userId) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getIsActive, true)
               .and(w -> w.eq(PromptTemplate::getType, "official")
                         .or()
                         .eq(PromptTemplate::getUserId, userId))
               .orderByDesc(PromptTemplate::getType) // 官方模板在前
               .orderByDesc(PromptTemplate::getCreatedTime);
        
        return list(wrapper);
    }

    /**
     * 根据ID获取模板内容
     */
    public String getTemplateContent(Long templateId) {
        if (templateId == null) {
            return null;
        }
        
        PromptTemplate template = getById(templateId);
        if (template == null || !template.getIsActive()) {
            logger.warn("模板不存在或已禁用: {}", templateId);
            return null;
        }
        
        // 增加使用次数
        template.setUsageCount(template.getUsageCount() + 1);
        updateById(template);
        
        return template.getContent();
    }

    /**
     * 创建用户自定义模板
     */
    public PromptTemplate createCustomTemplate(Long userId, String name, String content, String category, String description) {
        // 校验模板内容中的占位符
        Map<String, Object> validation = validatePlaceholders(content);
        if (!(Boolean) validation.get("valid")) {
            String errorMsg = (String) validation.get("message");
            logger.warn("模板占位符校验失败: {}", errorMsg);
            throw new RuntimeException(errorMsg);
        }

        PromptTemplate template = new PromptTemplate();
        template.setName(name);
        template.setContent(content);
        template.setType("custom");
        template.setUserId(userId);
        template.setCategory(category);
        template.setDescription(description);
        template.setIsActive(true);
        template.setUsageCount(0);

        save(template);
        logger.info("用户创建自定义模板: userId={}, templateId={}, 校验结果={}", userId, template.getId(), validation.get("message"));

        return template;
    }

    /**
     * 更新用户自定义模板
     */
    public boolean updateCustomTemplate(Long templateId, Long userId, String name, String content, String category, String description) {
        PromptTemplate template = getById(templateId);
        if (template == null) {
            logger.warn("模板不存在: {}", templateId);
            return false;
        }
        
        // 只能修改自己的模板
        if (!"custom".equals(template.getType()) || !userId.equals(template.getUserId())) {
            logger.warn("无权修改该模板: templateId={}, userId={}", templateId, userId);
            return false;
        }
        
        template.setName(name);
        template.setContent(content);
        template.setCategory(category);
        template.setDescription(description);
        
        return updateById(template);
    }

    /**
     * 删除用户自定义模板
     */
    public boolean deleteCustomTemplate(Long templateId, Long userId) {
        PromptTemplate template = getById(templateId);
        if (template == null) {
            return false;
        }
        
        // 只能删除自己的模板
        if (!"custom".equals(template.getType()) || !userId.equals(template.getUserId())) {
            logger.warn("无权删除该模板: templateId={}, userId={}", templateId, userId);
            return false;
        }
        
        return removeById(templateId);
    }

    /**
     * 获取默认模板ID（如果没有找到，返回null）
     */
    public Long getDefaultTemplateId() {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getType, "official")
               .eq(PromptTemplate::getName, "默认系统身份")
               .eq(PromptTemplate::getIsActive, true)
               .last("LIMIT 1");
        
        PromptTemplate template = getOne(wrapper);
        return template != null ? template.getId() : null;
    }

    /**
     * 获取公开模板列表（官方模板）
     */
    public List<PromptTemplate> getPublicTemplates(Long userId) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getType, "official")
               .eq(PromptTemplate::getIsActive, true)
               .orderByDesc(PromptTemplate::getUsageCount)
               .orderByDesc(PromptTemplate::getCreatedTime);
        
        List<PromptTemplate> templates = list(wrapper);
        
        // 标记收藏状态
        if (userId != null) {
            markFavoriteStatus(templates, userId);
        }
        
        return templates;
    }

    /**
     * 获取用户自定义模板列表
     */
    public List<PromptTemplate> getUserCustomTemplates(Long userId) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getType, "custom")
               .eq(PromptTemplate::getUserId, userId)
               .eq(PromptTemplate::getIsActive, true)
               .orderByDesc(PromptTemplate::getCreatedTime);
        
        List<PromptTemplate> templates = list(wrapper);
        
        // 标记收藏状态
        markFavoriteStatus(templates, userId);
        
        return templates;
    }

    /**
     * 获取用户收藏的模板列表
     */
    public List<PromptTemplate> getUserFavoriteTemplates(Long userId) {
        // 查询用户收藏的模板ID列表
        LambdaQueryWrapper<PromptTemplateFavorite> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.eq(PromptTemplateFavorite::getUserId, userId);
        
        List<PromptTemplateFavorite> favorites = favoriteRepository.selectList(favoriteWrapper);
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Long> templateIds = favorites.stream()
            .map(PromptTemplateFavorite::getTemplateId)
            .collect(Collectors.toList());
        
        // 查询模板详情
        LambdaQueryWrapper<PromptTemplate> templateWrapper = new LambdaQueryWrapper<>();
        templateWrapper.in(PromptTemplate::getId, templateIds)
                      .eq(PromptTemplate::getIsActive, true);
        
        List<PromptTemplate> templates = list(templateWrapper);
        
        // 收藏列表中的所有模板都标记为已收藏
        templates.forEach(template -> template.setIsFavorited(true));
        
        return templates;
    }

    /**
     * 收藏模板
     */
    public boolean favoriteTemplate(Long userId, Long templateId) {
        // 检查模板是否存在
        PromptTemplate template = getById(templateId);
        if (template == null || !template.getIsActive()) {
            logger.warn("模板不存在或已禁用: templateId={}", templateId);
            return false;
        }
        
        // 检查是否已收藏
        LambdaQueryWrapper<PromptTemplateFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplateFavorite::getUserId, userId)
               .eq(PromptTemplateFavorite::getTemplateId, templateId);
        
        if (favoriteRepository.selectCount(wrapper) > 0) {
            logger.info("用户已收藏该模板: userId={}, templateId={}", userId, templateId);
            return true; // 已收藏，返回成功
        }
        
        // 添加收藏
        PromptTemplateFavorite favorite = new PromptTemplateFavorite();
        favorite.setUserId(userId);
        favorite.setTemplateId(templateId);
        favorite.setCreatedTime(LocalDateTime.now());
        
        int result = favoriteRepository.insert(favorite);
        logger.info("用户收藏模板: userId={}, templateId={}, result={}", userId, templateId, result);
        
        return result > 0;
    }

    /**
     * 取消收藏模板
     */
    public boolean unfavoriteTemplate(Long userId, Long templateId) {
        LambdaQueryWrapper<PromptTemplateFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplateFavorite::getUserId, userId)
               .eq(PromptTemplateFavorite::getTemplateId, templateId);
        
        int result = favoriteRepository.delete(wrapper);
        logger.info("用户取消收藏模板: userId={}, templateId={}, result={}", userId, templateId, result);
        
        return result > 0;
    }

    /**
     * 检查用户是否收藏了某个模板
     */
    public boolean isFavorited(Long userId, Long templateId) {
        LambdaQueryWrapper<PromptTemplateFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplateFavorite::getUserId, userId)
               .eq(PromptTemplateFavorite::getTemplateId, templateId);
        
        return favoriteRepository.selectCount(wrapper) > 0;
    }
    
    /**
     * 根据分类获取模板列表
     */
    public List<PromptTemplate> getTemplatesByCategory(String category, Long userId) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getCategory, category)
               .eq(PromptTemplate::getIsActive, true)
               .and(w -> w.eq(PromptTemplate::getType, "official")
                         .or()
                         .eq(PromptTemplate::getUserId, userId))
               .orderByDesc(PromptTemplate::getIsDefault) // 默认模板在前
               .orderByDesc(PromptTemplate::getType) // 官方模板在前
               .orderByDesc(PromptTemplate::getUsageCount);
        
        List<PromptTemplate> templates = list(wrapper);
        
        // 标记收藏状态
        if (userId != null) {
            markFavoriteStatus(templates, userId);
        }
        
        return templates;
    }
    
    /**
     * 获取默认模板（按分类）
     */
    public PromptTemplate getDefaultTemplateByCategory(String category) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getCategory, category)
               .eq(PromptTemplate::getIsDefault, true)
               .eq(PromptTemplate::getIsActive, true)
               .eq(PromptTemplate::getType, "official")
               .last("LIMIT 1");
        
        return getOne(wrapper);
    }
    
    /**
     * 标记模板列表的收藏状态
     */
    private void markFavoriteStatus(List<PromptTemplate> templates, Long userId) {
        if (templates.isEmpty() || userId == null) {
            return;
        }

        // 查询用户收藏的所有模板ID
        LambdaQueryWrapper<PromptTemplateFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplateFavorite::getUserId, userId);

        List<Long> favoritedTemplateIds = favoriteRepository.selectList(wrapper).stream()
            .map(PromptTemplateFavorite::getTemplateId)
            .collect(Collectors.toList());

        // 标记每个模板的收藏状态
        templates.forEach(template -> {
            template.setIsFavorited(favoritedTemplateIds.contains(template.getId()));
        });
    }

    /**
     * 校验模板中的占位符
     * @param content 模板内容
     * @return 校验结果，包含 valid(boolean), message(String), missingRequired(List), unsupported(List)
     */
    public Map<String, Object> validatePlaceholders(String content) {
        Map<String, Object> result = new HashMap<>();
        List<String> missingRequired = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            result.put("valid", false);
            result.put("message", "模板内容不能为空");
            return result;
        }

        // 查找所有占位符 {xxx}
        Pattern pattern = Pattern.compile("\\{[^}]+\\}");
        Matcher matcher = pattern.matcher(content);
        Set<String> foundPlaceholders = new HashSet<>();

        while (matcher.find()) {
            String placeholder = matcher.group();
            foundPlaceholders.add(placeholder);

            // 检查是否是支持的占位符
            if (!SUPPORTED_PLACEHOLDERS.contains(placeholder)) {
                unsupported.add(placeholder);
            }
        }

        // 检查必填占位符是否存在
        for (String required : REQUIRED_PLACEHOLDERS) {
            if (!foundPlaceholders.contains(required)) {
                missingRequired.add(required);
            }
        }

        // 生成校验结果
        boolean valid = unsupported.isEmpty() && missingRequired.isEmpty();
        StringBuilder message = new StringBuilder();

        if (!missingRequired.isEmpty()) {
            message.append("缺少必填占位符: ").append(String.join(", ", missingRequired));
            for (String missing : missingRequired) {
                message.append("\n  - ").append(missing).append(": ").append(PLACEHOLDER_DESCRIPTIONS.get(missing));
            }
        }

        if (!unsupported.isEmpty()) {
            if (message.length() > 0) message.append("\n");
            message.append("不支持的占位符: ").append(String.join(", ", unsupported));
            message.append("\n支持的占位符有: ");
            for (Map.Entry<String, String> entry : PLACEHOLDER_DESCRIPTIONS.entrySet()) {
                message.append("\n  - ").append(entry.getKey()).append(": ").append(entry.getValue());
            }
        }

        if (valid) {
            message.append("占位符校验通过");
            if (!foundPlaceholders.isEmpty()) {
                message.append("，使用的占位符: ").append(String.join(", ", foundPlaceholders));
            }
        }

        result.put("valid", valid);
        result.put("message", message.toString());
        result.put("missingRequired", missingRequired);
        result.put("unsupported", unsupported);
        result.put("foundPlaceholders", new ArrayList<>(foundPlaceholders));

        return result;
    }

    /**
     * 获取支持的占位符说明
     */
    public Map<String, String> getPlaceholderDescriptions() {
        return new HashMap<>(PLACEHOLDER_DESCRIPTIONS);
    }
}

