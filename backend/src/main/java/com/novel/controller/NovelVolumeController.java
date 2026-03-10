package com.novel.controller;

import com.novel.domain.entity.NovelVolume;
import com.novel.service.NovelVolumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 小说卷控制器
 * 负责卷列表查询和批量操作
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/volumes")
@CrossOrigin
public class NovelVolumeController {

    @Autowired
    private NovelVolumeService novelVolumeService;

    /**
     * 获取小说的所有卷
     * 前端调用：novelVolumeService.getVolumesByNovelId()
     */
    @GetMapping("/novel/{novelId}")
    public ResponseEntity<?> getVolumesByNovelId(@PathVariable Long novelId) {
        try {
            List<NovelVolume> volumes = novelVolumeService.getVolumesByNovelId(novelId);
            return ResponseEntity.ok(volumes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    // DTO类
    public static class ErrorResponse {
        private String message;

        public ErrorResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
