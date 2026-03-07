package com.novel.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.TemplateDTO;
import com.novel.admin.service.AdminTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/templates")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminTemplateController {

    private final AdminTemplateService templateService;

    @GetMapping
    public Page<TemplateDTO> getTemplates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return templateService.getTemplates(category, type, page, size);
    }

    @GetMapping("/{id}")
    public TemplateDTO getTemplateById(@PathVariable Long id) {
        return templateService.getTemplateById(id);
    }

    @GetMapping("/{id}/stats")
    public Map<String, Object> getTemplateStats(@PathVariable Long id) {
        return templateService.getTemplateStats(id);
    }

    @PostMapping
    public void createTemplate(@RequestBody TemplateDTO templateDTO) {
        templateService.createTemplate(templateDTO);
    }

    @PutMapping("/{id}")
    public void updateTemplate(@PathVariable Long id, @RequestBody TemplateDTO templateDTO) {
        templateService.updateTemplate(id, templateDTO);
    }

    @DeleteMapping("/{id}")
    public void deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
    }

    @PostMapping("/{id}/set-default")
    public void setDefaultTemplate(@PathVariable Long id) {
        templateService.setDefaultTemplate(id);
    }

    @PutMapping("/{id}/sort-order")
    public void updateTemplateSortOrder(@PathVariable Long id, @RequestBody Map<String, Integer> request) {
        Integer sortOrder = request.get("sortOrder");
        templateService.updateTemplateSortOrder(id, sortOrder);
    }

    @PostMapping("/sort-order")
    public void batchUpdateSortOrder(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        java.util.List<Long> templateIds = (java.util.List<Long>) request.get("templateIds");
        templateService.batchUpdateSortOrder(templateIds);
    }
}
