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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 提示词模板服务
 */
@Service
public class PromptTemplateService extends ServiceImpl<PromptTemplateRepository, PromptTemplate> {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateService.class);

    @Autowired
    private PromptTemplateFavoriteRepository favoriteRepository;

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
        logger.info("用户创建自定义模板: userId={}, templateId={}", userId, template.getId());
        
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
}

