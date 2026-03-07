package com.novel.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.TemplateDTO;
import com.novel.admin.entity.Prompt;
import com.novel.admin.mapper.TemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminTemplateService {

    private final TemplateMapper templateMapper;

    public Page<TemplateDTO> getTemplates(String category, String type, int page, int size) {
        Page<TemplateDTO> pageParam = new Page<>(page, size);
        return templateMapper.selectTemplatePage(pageParam, category, type);
    }

    public TemplateDTO getTemplateById(Long id) {
        return templateMapper.selectTemplateById(id);
    }

    public void createTemplate(TemplateDTO templateDTO) {
        Prompt prompt = new Prompt();
        prompt.setName(templateDTO.getName());
        prompt.setCategory(templateDTO.getCategory());
        // 管理后台创建的模板默认为官方模板
        prompt.setType(templateDTO.getType() != null ? templateDTO.getType() : "official");
        prompt.setContent(templateDTO.getContent());
        prompt.setDescription(templateDTO.getDescription());
        prompt.setIsActive(true);
        prompt.setIsDefault(false);
        prompt.setSortOrder(0);
        prompt.setUsageCount(0);
        templateMapper.insert(prompt);
    }

    public void updateTemplate(Long id, TemplateDTO templateDTO) {
        Prompt prompt = templateMapper.selectById(id);
        if (prompt != null) {
            prompt.setName(templateDTO.getName());
            prompt.setCategory(templateDTO.getCategory());
            prompt.setType(templateDTO.getType());
            prompt.setContent(templateDTO.getContent());
            prompt.setDescription(templateDTO.getDescription());
            prompt.setIsActive(templateDTO.getIsActive());
            templateMapper.updateById(prompt);
        }
    }

    public void deleteTemplate(Long id) {
        templateMapper.deleteById(id);
    }
    
    public Map<String, Object> getTemplateStats(Long id) {
        Map<String, Object> stats = new HashMap<>();
        TemplateDTO template = templateMapper.selectTemplateById(id);
        if (template != null) {
            stats.put("usageCount", template.getUsageCount());
            stats.put("favoriteCount", template.getFavoriteCount());
        }
        return stats;
    }

    public void setDefaultTemplate(Long id) {
        Prompt template = templateMapper.selectById(id);
        if (template != null && "official".equals(template.getType())) {
            String category = template.getCategory();
            // 取消该分类下所有其他模板的默认状态
            templateMapper.clearDefaultByCategory(category);
            // 设置新的默认模板
            template.setIsDefault(true);
            templateMapper.updateById(template);
        }
    }

    public void updateTemplateSortOrder(Long id, Integer sortOrder) {
        Prompt template = templateMapper.selectById(id);
        if (template != null) {
            template.setSortOrder(sortOrder);
            templateMapper.updateById(template);
        }
    }

    public void batchUpdateSortOrder(java.util.List<Long> templateIds) {
        for (int i = 0; i < templateIds.size(); i++) {
            Long templateId = templateIds.get(i);
            Prompt template = templateMapper.selectById(templateId);
            if (template != null) {
                template.setSortOrder(i);
                templateMapper.updateById(template);
            }
        }
    }
}
