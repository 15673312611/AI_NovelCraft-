package com.novel.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.*;
import com.novel.admin.mapper.NovelDetailMapper;
import com.novel.admin.mapper.NovelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminNovelService {

    private final NovelMapper novelMapper;
    private final NovelDetailMapper novelDetailMapper;

    public Page<NovelDTO> getNovels(String keyword, int page, int size) {
        Page<NovelDTO> pageParam = new Page<>(page, size);
        return novelMapper.selectNovelPage(pageParam, keyword);
    }

    public NovelDTO getNovelById(Long id) {
        return novelMapper.selectNovelById(id);
    }

    public void deleteNovel(Long id) {
        novelMapper.deleteById(id);
    }

    public Map<String, Object> getNovelStats(Long id) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("chapterCount", novelMapper.getChapterCount(id));
        stats.put("wordCount", novelMapper.getWordCount(id));
        stats.put("characterCount", novelMapper.getCharacterCount(id));
        return stats;
    }
    
    /**
     * 获取小说详情（包含大纲、卷、章节、角色、世界观）
     */
    public Map<String, Object> getNovelDetail(Long id) {
        Map<String, Object> result = new HashMap<>();
        
        // 基本信息
        Map<String, Object> novel = novelDetailMapper.getNovelDetail(id);
        result.put("novel", novel);
        
        // 大纲
        Map<String, Object> outline = novelDetailMapper.getLatestOutlineByNovelId(id);
        result.put("outline", outline);
        
        // 卷列表
        List<Map<String, Object>> volumes = novelDetailMapper.getVolumesByNovelId(id);
        result.put("volumes", volumes);
        
        // 章节列表
        List<Map<String, Object>> chapters = novelDetailMapper.getChaptersByNovelId(id);
        result.put("chapters", chapters);
        
        // 角色列表
        List<Map<String, Object>> characters = novelDetailMapper.getCharactersByNovelId(id);
        result.put("characters", characters);
        
        // 世界观词典（可选）
        List<Map<String, Object>> worldview = novelDetailMapper.getWorldviewByNovelId(id);
        result.put("worldview", worldview);
        
        return result;
    }
}
