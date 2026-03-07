package com.novel.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.QimaoNovelDTO;
import com.novel.admin.service.AdminQimaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/qimao")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AdminQimaoController {

    private final AdminQimaoService qimaoService;

    @GetMapping("/novels")
    public Page<QimaoNovelDTO> getQimaoNovels(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return qimaoService.getQimaoNovels(keyword, category, status, page, size);
    }

    @GetMapping("/novels/{id}")
    public QimaoNovelDTO getQimaoNovelById(@PathVariable Long id) {
        return qimaoService.getQimaoNovelById(id);
    }

    @GetMapping("/stats")
    public Map<String, Object> getQimaoStats() {
        return qimaoService.getQimaoStats();
    }
}
