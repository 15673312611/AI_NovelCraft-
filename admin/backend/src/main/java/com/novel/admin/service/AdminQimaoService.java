package com.novel.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.QimaoNovelDTO;
import com.novel.admin.mapper.QimaoNovelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminQimaoService {

    private final QimaoNovelMapper qimaoNovelMapper;

    public Page<QimaoNovelDTO> getQimaoNovels(String keyword, String category, String status, int page, int size) {
        Page<QimaoNovelDTO> pageParam = new Page<>(page, size);
        return qimaoNovelMapper.selectQimaoNovelPage(pageParam, keyword, category, status);
    }

    public QimaoNovelDTO getQimaoNovelById(Long id) {
        return qimaoNovelMapper.selectQimaoNovelById(id);
    }

    public Map<String, Object> getQimaoStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", qimaoNovelMapper.getTotalCount());
        stats.put("totalChapterCount", qimaoNovelMapper.getTotalChapterCount());
        stats.put("todayCount", qimaoNovelMapper.getTodayCount());
        return stats;
    }
}
