package com.novel.controller;

import com.novel.common.Result;
import com.novel.domain.entity.PromptTemplate;
import com.novel.service.PromptTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 提示词模板Controller
 */
@RestController
@RequestMapping("/prompt-templates")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class PromptTemplateController {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateController.class);

    @Autowired
    private PromptTemplateService promptTemplateService;

    /**
     * 获取所有可用的模板
     * TODO: 从session或token中获取真实的userId
     */
    @GetMapping
    public Result<List<PromptTemplate>> getAvailableTemplates() {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            List<PromptTemplate> templates = promptTemplateService.getAvailableTemplates(userId);
            return Result.success(templates);
        } catch (Exception e) {
            logger.error("获取模板列表失败", e);
            return Result.error("获取模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取模板详情
     */
    @GetMapping("/{id}")
    public Result<PromptTemplate> getTemplateById(@PathVariable Long id) {
        try {
            PromptTemplate template = promptTemplateService.getById(id);
            if (template == null) {
                return Result.error("模板不存在");
            }
            return Result.success(template);
        } catch (Exception e) {
            logger.error("获取模板详情失败", e);
            return Result.error("获取模板详情失败: " + e.getMessage());
        }
    }

    /**
     * 创建用户自定义模板
     */
    @PostMapping
    public Result<PromptTemplate> createTemplate(@RequestBody Map<String, Object> request) {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            String name = (String) request.get("name");
            String content = (String) request.get("content");
            String category = (String) request.get("category");
            String description = (String) request.get("description");
            
            if (name == null || name.trim().isEmpty()) {
                return Result.error("模板名称不能为空");
            }
            if (content == null || content.trim().isEmpty()) {
                return Result.error("模板内容不能为空");
            }
            
            PromptTemplate template = promptTemplateService.createCustomTemplate(
                userId, name, content, category, description
            );
            
            return Result.success(template);
        } catch (Exception e) {
            logger.error("创建模板失败", e);
            return Result.error("创建模板失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户自定义模板
     */
    @PutMapping("/{id}")
    public Result<String> updateTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            String name = (String) request.get("name");
            String content = (String) request.get("content");
            String category = (String) request.get("category");
            String description = (String) request.get("description");
            
            boolean success = promptTemplateService.updateCustomTemplate(
                id, userId, name, content, category, description
            );
            
            if (success) {
                return Result.success("更新成功");
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            logger.error("更新模板失败", e);
            return Result.error("更新模板失败: " + e.getMessage());
        }
    }

    /**
     * 删除用户自定义模板
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteTemplate(@PathVariable Long id) {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            boolean success = promptTemplateService.deleteCustomTemplate(id, userId);
            
            if (success) {
                return Result.success("删除成功");
            } else {
                return Result.error("删除失败");
            }
        } catch (Exception e) {
            logger.error("删除模板失败", e);
            return Result.error("删除模板失败: " + e.getMessage());
        }
    }

    /**
     * 获取默认模板ID
     */
    @GetMapping("/default")
    public Result<Long> getDefaultTemplateId() {
        try {
            Long templateId = promptTemplateService.getDefaultTemplateId();
            return Result.success(templateId);
        } catch (Exception e) {
            logger.error("获取默认模板失败", e);
            return Result.error("获取默认模板失败: " + e.getMessage());
        }
    }

    /**
     * 获取公开模板列表
     */
    @GetMapping("/public")
    public Result<List<PromptTemplate>> getPublicTemplates() {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            List<PromptTemplate> templates = promptTemplateService.getPublicTemplates(userId);
            return Result.success(templates);
        } catch (Exception e) {
            logger.error("获取公开模板列表失败", e);
            return Result.error("获取公开模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户自定义模板列表
     */
    @GetMapping("/custom")
    public Result<List<PromptTemplate>> getUserCustomTemplates() {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            List<PromptTemplate> templates = promptTemplateService.getUserCustomTemplates(userId);
            return Result.success(templates);
        } catch (Exception e) {
            logger.error("获取自定义模板列表失败", e);
            return Result.error("获取自定义模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户收藏的模板列表
     */
    @GetMapping("/favorites")
    public Result<List<PromptTemplate>> getUserFavoriteTemplates() {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            List<PromptTemplate> templates = promptTemplateService.getUserFavoriteTemplates(userId);
            return Result.success(templates);
        } catch (Exception e) {
            logger.error("获取收藏模板列表失败", e);
            return Result.error("获取收藏模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 收藏模板
     */
    @PostMapping("/{id}/favorite")
    public Result<String> favoriteTemplate(@PathVariable Long id) {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            boolean success = promptTemplateService.favoriteTemplate(userId, id);
            if (success) {
                return Result.success("收藏成功");
            } else {
                return Result.error("收藏失败");
            }
        } catch (Exception e) {
            logger.error("收藏模板失败", e);
            return Result.error("收藏模板失败: " + e.getMessage());
        }
    }

    /**
     * 取消收藏模板
     */
    @DeleteMapping("/{id}/favorite")
    public Result<String> unfavoriteTemplate(@PathVariable Long id) {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            boolean success = promptTemplateService.unfavoriteTemplate(userId, id);
            if (success) {
                return Result.success("取消收藏成功");
            } else {
                return Result.error("取消收藏失败");
            }
        } catch (Exception e) {
            logger.error("取消收藏模板失败", e);
            return Result.error("取消收藏模板失败: " + e.getMessage());
        }
    }

    /**
     * 检查是否已收藏
     */
    @GetMapping("/{id}/is-favorited")
    public Result<Boolean> isFavorited(@PathVariable Long id) {
        try {
            // TODO: 从认证信息中获取真实的userId
            Long userId = 1L; // 临时写死
            
            boolean favorited = promptTemplateService.isFavorited(userId, id);
            return Result.success(favorited);
        } catch (Exception e) {
            logger.error("检查收藏状态失败", e);
            return Result.error("检查收藏状态失败: " + e.getMessage());
        }
    }
}

