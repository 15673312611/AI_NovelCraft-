package com.novel.controller;

import com.novel.entity.ChapterAnalysis;
import com.novel.service.ChapterAnalysisService;
import com.novel.common.ApiResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/novels/{novelId}/analysis")
public class ChapterAnalysisController {

    @Autowired
    private ChapterAnalysisService chapterAnalysisService;

    @Data
    public static class AnalysisRequest {
        private List<String> analysisTypes;
        private Integer startChapter;
        private Integer endChapter;
    }

    @PostMapping
    public ApiResponse<ChapterAnalysis> createAnalysis(
            @PathVariable Long novelId,
            @RequestBody AnalysisRequest request) {
        
        if (request.getAnalysisTypes() == null || request.getAnalysisTypes().isEmpty()) {
            return ApiResponse.error("分析类型不能为空");
        }

        if (request.getStartChapter() == null || request.getEndChapter() == null) {
            return ApiResponse.error("章节范围不能为空");
        }

        if (request.getStartChapter() > request.getEndChapter()) {
            return ApiResponse.error("起始章节不能大于结束章节");
        }

        try {
            // 目前只处理第一个分析类型，后续可以支持批量
            String analysisType = request.getAnalysisTypes().get(0);
            ChapterAnalysis analysis = chapterAnalysisService.analyzeChapters(
                novelId, 
                analysisType, 
                request.getStartChapter(), 
                request.getEndChapter()
            );
            return ApiResponse.success(analysis);
        } catch (Exception e) {
            log.error("创建章节分析失败", e);
            return ApiResponse.error("分析失败: " + e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<List<ChapterAnalysis>> getAnalyses(@PathVariable Long novelId) {
        try {
            List<ChapterAnalysis> analyses = chapterAnalysisService.getAnalysesByNovelId(novelId);
            return ApiResponse.success(analyses);
        } catch (Exception e) {
            log.error("获取章节分析列表失败", e);
            return ApiResponse.error("获取失败: " + e.getMessage());
        }
    }

    @GetMapping("/{analysisId}")
    public ApiResponse<ChapterAnalysis> getAnalysis(
            @PathVariable Long novelId,
            @PathVariable Long analysisId) {
        try {
            ChapterAnalysis analysis = chapterAnalysisService.getAnalysisById(analysisId);
            if (analysis == null || !analysis.getNovelId().equals(novelId)) {
                return ApiResponse.error("分析记录不存在");
            }
            return ApiResponse.success(analysis);
        } catch (Exception e) {
            log.error("获取章节分析失败", e);
            return ApiResponse.error("获取失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{analysisId}")
    public ApiResponse<Void> deleteAnalysis(
            @PathVariable Long novelId,
            @PathVariable Long analysisId) {
        try {
            ChapterAnalysis analysis = chapterAnalysisService.getAnalysisById(analysisId);
            if (analysis == null || !analysis.getNovelId().equals(novelId)) {
                return ApiResponse.error("分析记录不存在");
            }
            chapterAnalysisService.deleteAnalysis(analysisId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除章节分析失败", e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }
}

