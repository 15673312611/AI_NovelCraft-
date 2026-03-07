package com.novel.admin.service;

import com.novel.admin.entity.*;
import com.novel.admin.mapper.NovelDetailMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小说详情服务
 */
@Service
public class NovelDetailService {

    @Autowired
    private NovelDetailMapper novelDetailMapper;

    /**
     * 获取小说基本信息
     */
    public Novel getNovelById(Long novelId) {
        return novelDetailMapper.selectNovelById(novelId);
    }

    /**
     * 获取小说大纲
     */
    public NovelOutline getOutlineByNovelId(Long novelId) {
        return novelDetailMapper.selectOutlineByNovelId(novelId);
    }

    /**
     * 获取分卷列表
     */
    public List<NovelVolume> getVolumesByNovelId(Long novelId) {
        return novelDetailMapper.selectVolumesByNovelId(novelId);
    }

    /**
     * 获取章纲列表（按小说ID）
     */
    public List<VolumeChapterOutline> getChapterOutlinesByNovelId(Long novelId, String status, Integer page, Integer size) {
        Integer offset = (page - 1) * size;
        return novelDetailMapper.selectChapterOutlinesByNovelId(novelId, status, offset, size);
    }

    /**
     * 获取章纲列表（按卷ID）
     */
    public List<VolumeChapterOutline> getChapterOutlinesByVolumeId(Long volumeId) {
        return novelDetailMapper.selectChapterOutlinesByVolumeId(volumeId);
    }

    /**
     * 获取章节列表
     */
    public List<Chapter> getChaptersByNovelId(Long novelId, String status, Integer page, Integer size) {
        Integer offset = (page - 1) * size;
        return novelDetailMapper.selectChaptersByNovelId(novelId, status, offset, size);
    }

    /**
     * 获取小说完整详情
     */
    public Map<String, Object> getNovelDetailAll(Long novelId) {
        Map<String, Object> result = new HashMap<>();
        
        // 获取所有数据
        Novel novel = getNovelById(novelId);
        NovelOutline outline = getOutlineByNovelId(novelId);
        List<NovelVolume> volumes = getVolumesByNovelId(novelId);
        List<VolumeChapterOutline> chapterOutlines = novelDetailMapper.selectAllChapterOutlinesByNovelId(novelId);
        List<Chapter> chapters = novelDetailMapper.selectAllChaptersByNovelId(novelId);
        
        result.put("novel", novel);
        result.put("outline", outline);
        result.put("volumes", volumes);
        result.put("chapterOutlines", chapterOutlines);
        result.put("chapters", chapters);
        
        // 添加统计信息
        result.put("statistics", getNovelStatistics(novelId));
        
        return result;
    }

    /**
     * 获取小说统计信息
     */
    public Map<String, Object> getNovelStatistics(Long novelId) {
        return novelDetailMapper.selectNovelStatistics(novelId);
    }
    
    /**
     * 根据ID获取章节详情
     */
    public Chapter getChapterById(Long chapterId) {
        return novelDetailMapper.selectChapterById(chapterId);
    }
    
    /**
     * 更新章节
     */
    public boolean updateChapter(Chapter chapter) {
        return novelDetailMapper.updateChapter(chapter) > 0;
    }
}
