package com.novel.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.*;
import com.novel.admin.service.AdminNovelService;
import com.novel.admin.service.GraphDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/novels")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminNovelController {

    private final AdminNovelService novelService;
    private final GraphDataService graphDataService;
    private final com.novel.admin.service.NovelDetailService novelDetailService;

    @GetMapping
    public Page<NovelDTO> getNovels(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return novelService.getNovels(keyword, page, size);
    }

    @GetMapping("/{id}")
    public NovelDTO getNovelById(@PathVariable Long id) {
        return novelService.getNovelById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteNovel(@PathVariable Long id) {
        novelService.deleteNovel(id);
    }

    @GetMapping("/{id}/stats")
    public Object getNovelStats(@PathVariable Long id) {
        return novelService.getNovelStats(id);
    }
    
    /**
     * 获取小说图谱数据
     */
    @GetMapping("/{id}/graph")
    public GraphDataDTO getGraphData(@PathVariable Long id) {
        return graphDataService.getGraphData(id);
    }

    // ========== 新增：详细数据接口（合并为一个） ==========

    /**
     * 获取单个章节详情（包含完整内容）
     * 接口路径: GET /admin/novels/{novelId}/chapters/{chapterId}
     */
    @GetMapping("/{novelId}/chapters/{chapterId}")
    public java.util.Map<String, Object> getChapterDetail(@PathVariable Long novelId, @PathVariable Long chapterId) {
        System.out.println("========== 获取章节详情，novelId: " + novelId + ", chapterId: " + chapterId + " ==========");
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        try {
            com.novel.admin.entity.Chapter chapter = novelDetailService.getChapterById(chapterId);
            
            if (chapter == null || !chapter.getNovelId().equals(novelId)) {
                result.put("code", 404);
                result.put("message", "章节不存在");
                return result;
            }
            
            result.put("code", 200);
            result.put("data", chapter);
            result.put("message", "查询成功");
            
            System.out.println("章节详情: " + chapter.getTitle());
            
        } catch (Exception e) {
            System.err.println("查询章节失败: " + e.getMessage());
            e.printStackTrace();
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
        }
        
        return result;   
    }
    
    /**
     * 更新章节
     * 接口路径: PUT /admin/novels/{novelId}/chapters/{chapterId}
     */
    @PutMapping("/{novelId}/chapters/{chapterId}")
    public java.util.Map<String, Object> updateChapter(
            @PathVariable Long novelId, 
            @PathVariable Long chapterId,
            @RequestBody com.novel.admin.entity.Chapter chapter) {
        System.out.println("========== 更新章节，novelId: " + novelId + ", chapterId: " + chapterId + " ==========");
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        try {
            // 验证章节是否存在
            com.novel.admin.entity.Chapter existingChapter = novelDetailService.getChapterById(chapterId);
            if (existingChapter == null || !existingChapter.getNovelId().equals(novelId)) {
                result.put("code", 404);
                result.put("message", "章节不存在");
                return result;
            }
            
            // 设置 ID
            chapter.setId(chapterId);
            chapter.setNovelId(novelId);
            
            // 更新章节
            boolean success = novelDetailService.updateChapter(chapter);
            
            if (success) {
                result.put("code", 200);
                result.put("data", chapter);
                result.put("message", "更新成功");
                System.out.println("章节更新成功: " + chapter.getTitle());
            } else {
                result.put("code", 500);
                result.put("message", "更新失败");
            }
            
        } catch (Exception e) {
            System.err.println("更新章节失败: " + e.getMessage());
            e.printStackTrace();
            result.put("code", 500);
            result.put("message", "更新失败: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取小说完整详情（一次性获取所有数据）
     * 接口路径: GET /admin/novels/{novelId}/detail
     */
    @GetMapping("/{novelId}/detail")
    public java.util.Map<String, Object> getNovelDetail(@PathVariable Long novelId) {
        System.out.println("========== 开始查询小说详情，novelId: " + novelId + " ==========");
        
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        try {
            // 1. 查询小说基本信息
            com.novel.admin.entity.Novel novel = novelDetailService.getNovelById(novelId);
            System.out.println("小说基本信息: " + (novel != null ? novel.getTitle() : "null"));
            
            // 2. 查询大纲
            com.novel.admin.entity.NovelOutline outline = novelDetailService.getOutlineByNovelId(novelId);
            System.out.println("大纲: " + (outline != null ? outline.getTitle() : "null"));
            
            // 3. 查询分卷
            java.util.List<com.novel.admin.entity.NovelVolume> volumes = novelDetailService.getVolumesByNovelId(novelId);
            System.out.println("分卷数量: " + (volumes != null ? volumes.size() : 0));
            
            // 4. 查询章纲
            java.util.List<com.novel.admin.entity.VolumeChapterOutline> chapterOutlines = 
                novelDetailService.getChapterOutlinesByNovelId(novelId, null, 1, 1000);
            System.out.println("章纲数量: " + (chapterOutlines != null ? chapterOutlines.size() : 0));
            
            // 5. 查询章节
            java.util.List<com.novel.admin.entity.Chapter> chapters = 
                novelDetailService.getChaptersByNovelId(novelId, null, 1, 1000);
            System.out.println("章节数量: " + (chapters != null ? chapters.size() : 0));
            
            // 6. 组装返回数据
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("novel", novel);
            data.put("outline", outline);
            data.put("volumes", volumes);
            data.put("chapterOutlines", chapterOutlines);
            data.put("chapters", chapters);
            
            result.put("code", 200);
            result.put("data", data);
            result.put("message", "查询成功");
            
            System.out.println("========== 查询完成 ==========");
            
        } catch (Exception e) {
            System.err.println("查询失败: " + e.getMessage());
            e.printStackTrace();
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
        }
        
        return result;
    }
}
