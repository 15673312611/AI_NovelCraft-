package com.novel.controller;

import com.novel.common.Result;
import com.novel.common.security.AuthUtils;
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
     */
    @GetMapping
    public Result<List<PromptTemplate>> getAvailableTemplates(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            List<PromptTemplate> templates = promptTemplateService.getAvailableTemplates(userId, type, category);
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
            Long userId = AuthUtils.getCurrentUserId();
            PromptTemplate template = promptTemplateService.getById(id);
            if (template == null) {
                return Result.error("模板不存在");
            }
            
            // 验证权限：只能查看公开模板、官方模板或自己的模板
            String type = template.getType();
            if (!"official".equals(type) && 
                !"public".equals(type) && 
                (template.getUserId() == null || !template.getUserId().equals(userId))) {
                return Result.error("无权查看此模板");
            }

            // 仅自定义模板允许返回内容，官方/公开模板不返回
            if (!"custom".equals(type)) {
                template.setContent(null);
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
            Long userId = AuthUtils.getCurrentUserId();
            
            String name = (String) request.get("name");
            String content = (String) request.get("content");
            String description = (String) request.get("description");
            
            if (name == null || name.trim().isEmpty()) {
                return Result.error("模板名称不能为空");
            }
            if (content == null || content.trim().isEmpty()) {
                return Result.error("模板内容不能为空");
            }
            
            PromptTemplate template = promptTemplateService.createCustomTemplate(
                userId, name, content, "chapter", description
            );
            // 不对前端返回提示词内容
            template.setContent(null);
            
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
            Long userId = AuthUtils.getCurrentUserId();
            
            String name = (String) request.get("name");
            String content = (String) request.get("content");
            String category = (String) request.get("category");
            String description = (String) request.get("description");
            
            boolean success = promptTemplateService.updateCustomTemplate(
                id, userId, name, content, "chapter", description
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
            Long userId = AuthUtils.getCurrentUserId();
            
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
    public Result<List<PromptTemplate>> getPublicTemplates(@RequestParam(required = false) String category) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            logger.info("🔍 获取公开模板列表: userId={}, category={}", userId, category);
            List<PromptTemplate> templates = promptTemplateService.getPublicTemplates(userId, category);
            logger.info("✅ 获取公开模板成功: 数量={}", templates.size());
            return Result.success(templates);
        } catch (Exception e) {
            logger.error("❌ 获取公开模板列表失败", e);
            return Result.error("获取公开模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户自定义模板列表
     */
    @GetMapping("/custom")
    public Result<List<PromptTemplate>> getUserCustomTemplates(@RequestParam(required = false) String category) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            List<PromptTemplate> templates = promptTemplateService.getUserCustomTemplates(userId, category);
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
    public Result<List<PromptTemplate>> getUserFavoriteTemplates(@RequestParam(required = false) String category) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            List<PromptTemplate> templates = promptTemplateService.getUserFavoriteTemplates(userId, category);
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
            Long userId = AuthUtils.getCurrentUserId();
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
            Long userId = AuthUtils.getCurrentUserId();
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
            Long userId = AuthUtils.getCurrentUserId();
            boolean favorited = promptTemplateService.isFavorited(userId, id);
            return Result.success(favorited);
        } catch (Exception e) {
            logger.error("检查收藏状态失败", e);
            return Result.error("检查收藏状态失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据分类获取模板列表
     */
    @GetMapping("/category/{category}")
    public Result<List<PromptTemplate>> getTemplatesByCategory(@PathVariable String category) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            List<PromptTemplate> templates = promptTemplateService.getTemplatesByCategory(category, userId);
            return Result.success(templates);
        } catch (Exception e) {
            logger.error("根据分类获取模板列表失败", e);
            return Result.error("获取模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取支持的占位符说明
     */
    @GetMapping("/placeholders")
    public Result<Map<String, String>> getPlaceholders() {
        try {
            Map<String, String> placeholders = promptTemplateService.getPlaceholderDescriptions();
            return Result.success(placeholders);
        } catch (Exception e) {
            logger.error("获取占位符说明失败", e);
            return Result.error("获取占位符说明失败: " + e.getMessage());
        }
    }

    /**
     * 校验模板内容中的占位符
     */
    @PostMapping("/validate")
    public Result<Map<String, Object>> validateTemplate(@RequestBody Map<String, String> request) {
        try {
            String content = request.get("content");
            if (content == null || content.trim().isEmpty()) {
                return Result.error("模板内容不能为空");
            }

            Map<String, Object> validation = promptTemplateService.validatePlaceholders(content);
            return Result.success(validation);
        } catch (Exception e) {
            logger.error("校验模板失败", e);
            return Result.error("校验模板失败: " + e.getMessage());
        }
    }

    /**
     * 设置默认模板
     */
    @PostMapping("/{id}/set-default")
    public Result<String> setDefaultTemplate(@PathVariable Long id) {
        try {
            boolean success = promptTemplateService.setDefaultTemplate(id);
            if (success) {
                return Result.success("设置默认模板成功");
            } else {
                return Result.error("设置默认模板失败");
            }
        } catch (Exception e) {
            logger.error("设置默认模板失败", e);
            return Result.error("设置默认模板失败: " + e.getMessage());
        }
    }

    /**
     * 批量更新模板排序
     */
    @PostMapping("/sort-order")
    public Result<String> updateTemplatesSortOrder(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> templateIds = (List<Long>) request.get("templateIds");
            
            if (templateIds == null || templateIds.isEmpty()) {
                return Result.error("模板ID列表不能为空");
            }
            
            boolean success = promptTemplateService.updateTemplatesSortOrder(templateIds);
            if (success) {
                return Result.success("更新排序成功");
            } else {
                return Result.error("更新排序失败");
            }
        } catch (Exception e) {
            logger.error("更新模板排序失败", e);
            return Result.error("更新模板排序失败: " + e.getMessage());
        }
    }

    /**
     * 更新单个模板的排序
     */
    @PutMapping("/{id}/sort-order")
    public Result<String> updateTemplateSortOrder(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> request) {
        try {
            Integer sortOrder = request.get("sortOrder");
            if (sortOrder == null) {
                return Result.error("排序值不能为空");
            }
            
            boolean success = promptTemplateService.updateTemplateSortOrder(id, sortOrder);
            if (success) {
                return Result.success("更新排序成功");
            } else {
                return Result.error("更新排序失败");
            }
        } catch (Exception e) {
            logger.error("更新模板排序失败", e);
            return Result.error("更新模板排序失败: " + e.getMessage());
        }
    }
}
