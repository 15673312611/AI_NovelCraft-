package com.novel.controller;

import com.novel.domain.entity.Novel;
import com.novel.domain.entity.Chapter;
import com.novel.domain.entity.Character;
import com.novel.service.NovelService;
import com.novel.service.ChapterService;
import com.novel.service.ProtagonistStatusService;
import com.novel.repository.CharacterRepository;
import com.novel.common.security.SecurityUtils;
import com.novel.common.security.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小说控制器
 * 
 * @author Novel Creation System
 * @version 1.0.0
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/novels")
@CrossOrigin(originPatterns = {"http://localhost:*", "http://127.0.0.1:*"}, allowCredentials = "true")
public class NovelController {

    private static final Logger logger = LoggerFactory.getLogger(NovelController.class);

    @Autowired
    private NovelService novelService;

    @Autowired
    private ChapterService chapterService;

    @Autowired
    private CharacterRepository characterRepository;
    
    @Autowired
    private ProtagonistStatusService protagonistStatusService;
    
    @Autowired
    private com.novel.service.AIWritingService aiWritingService;
    /**
     * 获取小说大纲（直接来自 novels.outline）
     */
    @GetMapping("/{novelId}/outline")
    public ResponseEntity<Object> getNovelOutline(@PathVariable Long novelId) {
        try {
            Novel novel = novelService.getById(novelId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("novelId", novelId);
            resp.put("outline", novel != null ? novel.getOutline() : null);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    /**
     * 覆盖保存小说大纲（写入 novels.outline）
     */
    @PutMapping("/{novelId}/outline")
    public ResponseEntity<Object> updateNovelOutline(@PathVariable Long novelId, @RequestBody Map<String, Object> request) {
        try {
            String outline = request != null && request.get("outline") instanceof String ? (String) request.get("outline") : null;
            Novel novel = novelService.getById(novelId);
            if (novel == null) {
                Map<String, Object> err = new HashMap<>();
                err.put("message", "小说不存在");
                return ResponseEntity.badRequest().body(err);
            }
            novel.setOutline(outline);
            novelService.update(novel);
            Map<String, Object> resp = new HashMap<>();
            resp.put("novelId", novelId);
            resp.put("outline", outline);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(err);
        }
    }

    /**
     * 获取小说列表
     */
    @GetMapping
    public ResponseEntity<Object> getNovels(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            // 获取当前登录用户ID
            Long userId = AuthUtils.getCurrentUserId();
            
            IPage<Novel> novelsPage = novelService.getNovelsByAuthor(userId, page, size);

            Map<String, Object> response = new HashMap<>();
            response.put("content", novelsPage.getRecords());
            response.put("totalElements", novelsPage.getTotal());
            response.put("totalPages", novelsPage.getPages());
            response.put("size", novelsPage.getSize());
            response.put("number", novelsPage.getCurrent() - 1);
            response.put("first", novelsPage.getCurrent() == 1);
            response.put("last", novelsPage.getCurrent() == novelsPage.getPages());
            response.put("empty", novelsPage.getRecords().isEmpty());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // 临时返回空数据，避免前端报错
            Map<String, Object> response = new HashMap<>();
            response.put("content", new ArrayList<>());
            response.put("totalElements", 0);
            response.put("totalPages", 0);
            response.put("size", size);
            response.put("number", page);
            response.put("first", true);
            response.put("last", true);
            response.put("empty", true);

            return ResponseEntity.ok(response);
        }
    }

    /**
     * 测试数据库连接
     */
    @GetMapping("/test")
    public ResponseEntity<?> testDatabase() {
        try {
            // 尝试查询数据库
            IPage<Novel> novels = novelService.getNovels(0, 1);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "数据库连接正常");
            response.put("totalNovels", novels.getTotal());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "数据库连接失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 获取单个小说
     */
    @GetMapping("/{id}")
    public ResponseEntity<Novel> getNovel(@PathVariable Long id) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            Novel novel = novelService.getNovel(id);
            if (novel == null) {
                return ResponseEntity.notFound().build();
            }
            // 验证权限
            if (novel.getAuthorId() == null || !novel.getAuthorId().equals(userId)) {
                return ResponseEntity.status(403).build();
            }
            return ResponseEntity.ok(novel);
        } catch (Exception e) {
            logger.error("获取小说失败: novelId={}", id, e);
            return ResponseEntity.status(500).build();
        }
    }



    /**
     * 兼容性方法：直接接收小说对象
     */
    @PostMapping("/simple")
    public ResponseEntity<?> createSimpleNovel(@RequestBody Novel novel) {
        try {
            System.out.println("接收到简单创建小说请求: " + novel.getTitle());
            
            // 从认证信息中获取当前用户ID（过滤器已校验登录）
            Long userId = AuthUtils.getCurrentUserId();
            Novel createdNovel = novelService.createNovel(novel, userId);
            System.out.println("小说创建成功，ID: " + createdNovel.getId() + ", 作者ID: " + userId);
            return ResponseEntity.ok(createdNovel);
        } catch (Exception e) {
            System.err.println("创建小说失败: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "创建小说失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 更新小说
     */
    @PutMapping("/{id}")
    public ResponseEntity<Novel> updateNovel(@PathVariable Long id, @RequestBody Novel novel) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            Novel existingNovel = novelService.getNovel(id);
            if (existingNovel == null) {
                return ResponseEntity.notFound().build();
            }
            // 验证权限
            if (existingNovel.getAuthorId() == null || !existingNovel.getAuthorId().equals(userId)) {
                return ResponseEntity.status(403).build();
            }
            
            Novel updatedNovel = novelService.updateNovel(id, novel);
            return ResponseEntity.ok(updatedNovel);
        } catch (Exception e) {
            logger.error("更新小说失败: novelId={}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 删除小说
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNovel(@PathVariable Long id) {
        try {
            Long userId = AuthUtils.getCurrentUserId();
            Novel novel = novelService.getNovel(id);
            if (novel == null) {
                return ResponseEntity.notFound().build();
            }
            // 验证权限
            if (novel.getAuthorId() == null || !novel.getAuthorId().equals(userId)) {
                logger.warn("用户{}尝试删除不属于自己的小说{}", userId, id);
                return ResponseEntity.status(403).build();
            }
            
            boolean deleted = novelService.deleteNovel(id);
            return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
        } catch (Exception e) {
            logger.error("删除小说失败: novelId={}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取小说的章节列表（优化版，排除content等大文本字段）
     */
    @GetMapping("/{id}/chapters")
    public ResponseEntity<Object> getNovelChapters(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            if (size == 0) {
                // 如果size为0，返回所有章节的元数据（排除content等大字段）
                java.util.List<Chapter> chapters = chapterService.getChapterMetadataByNovel(id);
                return ResponseEntity.ok(chapters);
            } else {
                // 分页查询（已优化，排除content等大字段）
                IPage<Chapter> chaptersPage = chapterService.getChaptersByNovel(id, page, size);

                Map<String, Object> response = new HashMap<>();
                response.put("content", chaptersPage.getRecords());
                response.put("totalElements", chaptersPage.getTotal());
                response.put("totalPages", chaptersPage.getPages());
                response.put("size", chaptersPage.getSize());
                response.put("number", chaptersPage.getCurrent() - 1); // MyBatis Plus从1开始，前端从0开始
                response.put("first", chaptersPage.getCurrent() == 1);
                response.put("last", chaptersPage.getCurrent() == chaptersPage.getPages());
                response.put("empty", chaptersPage.getRecords().isEmpty());

                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            // 临时返回空数据，避免前端报错
            return ResponseEntity.ok(new java.util.ArrayList<>());
        }
    }

    /**
     * 获取小说的章节统计
     */
    @GetMapping("/{id}/chapters/statistics")
    public ResponseEntity<Map<String, Object>> getNovelChapterStatistics(@PathVariable Long id) {
        try {
            Map<String, Object> stats = chapterService.getNovelChapterStatistics(id);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            // 返回默认统计信息
            Map<String, Object> defaultStats = new HashMap<>();
            defaultStats.put("totalChapters", 0);
            defaultStats.put("totalWords", 0);
            defaultStats.put("averageWordsPerChapter", 0);
            defaultStats.put("lastUpdated", "");
            defaultStats.put("completionRate", 0);
            return ResponseEntity.ok(defaultStats);
        }
    }

    // ================================
    // 私有辅助方法
    // ================================

    /**
     * 从Map构建Novel对象
     */
    private Novel buildNovelFromMap(Map<String, Object> novelData) {
        Novel novel = new Novel();
        
        if (novelData.get("title") != null) {
            novel.setTitle((String) novelData.get("title"));
        }
        if (novelData.get("author") != null) {
            novel.setAuthor((String) novelData.get("author"));
        }
        if (novelData.get("genre") != null) {
            novel.setGenre((String) novelData.get("genre"));
        }
        if (novelData.get("description") != null) {
            novel.setDescription((String) novelData.get("description"));
        }
        if (novelData.get("tags") != null) {
            novel.setTags((String) novelData.get("tags"));
        }
        if (novelData.get("status") != null) {
            String statusStr = (String) novelData.get("status");
            try {
                novel.setStatus(Novel.NovelStatus.valueOf(statusStr));
            } catch (IllegalArgumentException e) {
                novel.setStatus(Novel.NovelStatus.DRAFT); // 默认状态
            }
        } else {
            novel.setStatus(Novel.NovelStatus.DRAFT); // 默认状态
        }
        if (novelData.get("outline") != null) {
            novel.setOutline((String) novelData.get("outline"));
        }
        if (novelData.get("wordCount") != null) {
            novel.setWordCount((Integer) novelData.get("wordCount"));
        } else {
            novel.setWordCount(0); // 默认字数
        }
        if (novelData.get("chapterCount") != null) {
            novel.setChapterCount((Integer) novelData.get("chapterCount"));
        } else {
            novel.setChapterCount(0); // 默认章节数
        }
        
        // 添加新的创作配置字段支持（安全的类型转换）
        if (novelData.get("targetTotalChapters") != null) {
            Object value = novelData.get("targetTotalChapters");
            novel.setTargetTotalChapters(convertToInteger(value, "targetTotalChapters"));
        }
        if (novelData.get("wordsPerChapter") != null) {
            Object value = novelData.get("wordsPerChapter");
            novel.setWordsPerChapter(convertToInteger(value, "wordsPerChapter"));
        }
        if (novelData.get("plannedVolumeCount") != null) {
            Object value = novelData.get("plannedVolumeCount");
            novel.setPlannedVolumeCount(convertToInteger(value, "plannedVolumeCount"));
        }
        if (novelData.get("totalWordTarget") != null) {
            Object value = novelData.get("totalWordTarget");
            novel.setTotalWordTarget(convertToInteger(value, "totalWordTarget"));
        }
        
        return novel;
    }

    /**
     * 从Map构建Character对象
     */
    private Character buildCharacterFromMap(Map<String, Object> characterData, Long novelId) {
        Character character = new Character();
        
        character.setNovelId(novelId);
        
        if (characterData.get("name") != null) {
            character.setName((String) characterData.get("name"));
        }
        if (characterData.get("alias") != null) {
            character.setAlias((String) characterData.get("alias"));
        }
        if (characterData.get("description") != null) {
            character.setDescription((String) characterData.get("description"));
        }
        if (characterData.get("appearance") != null) {
            character.setAppearance((String) characterData.get("appearance"));
        }
        if (characterData.get("personality") != null) {
            character.setPersonality((String) characterData.get("personality"));
        }
        if (characterData.get("background") != null) {
            character.setBackground((String) characterData.get("background"));
        }
        if (characterData.get("motivation") != null) {
            character.setMotivation((String) characterData.get("motivation"));
        }
        if (characterData.get("goals") != null) {
            character.setGoals((String) characterData.get("goals"));
        }
        if (characterData.get("relationships") != null) {
            character.setRelationships((String) characterData.get("relationships"));
        }
        if (characterData.get("tags") != null) {
            character.setTags((String) characterData.get("tags"));
        }
        
        // 设置角色类型
        if (characterData.get("characterType") != null) {
            character.setCharacterType((String) characterData.get("characterType"));
        } else {
            character.setCharacterType("MINOR"); // 默认为次要角色
        }
        
        // 设置状态
        if (characterData.get("status") != null) {
            character.setStatus((String) characterData.get("status"));
        } else {
            character.setStatus("ACTIVE"); // 默认为活跃状态
        }
        
        // 设置初始重要性评分
        if (characterData.get("importanceScore") != null) {
            character.setImportanceScore((Integer) characterData.get("importanceScore"));
        } else {
            character.setImportanceScore(calculateInitialImportanceScore(character.getCharacterType()));
        }
        
        // 初始化出场相关字段
        character.setAppearanceCount(0);
        character.setFirstAppearanceChapter(null);
        character.setLastAppearanceChapter(null);
        
        return character;
    }

    /**
     * 根据角色类型计算初始重要性评分
     */
    private Integer calculateInitialImportanceScore(String characterType) {
        switch (characterType) {
            case "PROTAGONIST":
                return 100;
            case "MAJOR":
                return 60;
            case "MINOR":
                return 30;
            default:
                return 10;
        }
    }
    
    /**
     * 安全的类型转换 - 将Object转换为Integer
     */
    private Integer convertToInteger(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        
        try {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof Number) {
                return ((Number) value).intValue();
            } else if (value instanceof String) {
                String strValue = ((String) value).trim();
                if (strValue.isEmpty()) {
                    return null;
                }
                return Integer.valueOf(strValue);
            } else {
                logger.warn("⚠️ 字段{}的值类型不支持: {}", fieldName, value.getClass().getSimpleName());
                return null;
            }
        } catch (NumberFormatException e) {
            logger.error("❌ 字段{}的值无法转换为整数: {}", fieldName, value);
            return null;
        }
    }
    

    

    /**
     * 获取小说的创作状态
     */
    @GetMapping("/{id}/creation-stage")
    public ResponseEntity<Object> getCreationStage(@PathVariable Long id) {
        try {
            Novel novel = novelService.getById(id);
            if (novel == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "小说不存在");
                return ResponseEntity.status(404).body(errorResponse);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("novelId", id);
            response.put("creationStage", novel.getCreationStage());
            response.put("creationStageDescription", novel.getCreationStage().getDescription());
            response.put("status", novel.getStatus());
            response.put("statusDescription", novel.getStatus().getDescription());
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("获取创作状态失败: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取创作状态失败");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * 获取可用于写作的书籍列表（已生成卷大纲的书籍）
     */
    @GetMapping("/writable")
    public ResponseEntity<Object> getWritableNovels() {
        try {
            // 从认证信息中获取当前用户ID（过滤器已校验登录）
            Long userId = AuthUtils.getCurrentUserId();
            List<Novel> novels = novelService.getWritableNovels(userId);
            
            logger.info("获取可写作书籍列表成功，数量: {}", novels.size());
            
            return ResponseEntity.ok(novels);
            
        } catch (Exception e) {
            logger.error("获取可写作书籍列表失败", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "获取书籍列表失败");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 生成小说脑洞简介
     */
    @PostMapping("/generate-synopsis")
    public ResponseEntity<Object> generateSynopsis(@RequestBody Map<String, Object> request) {
        try {
            String novelTitle = (String) request.get("title");
            
            if (novelTitle == null || novelTitle.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "书名不能为空");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // 获取AI配置
            @SuppressWarnings("unchecked")
            Map<String, Object> aiConfigMap = (Map<String, Object>) request.get("aiConfig");
            if (aiConfigMap == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "AI配置不能为空");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // 构建AI配置对象
            com.novel.dto.AIConfigRequest aiConfig = new com.novel.dto.AIConfigRequest();
            aiConfig.setProvider((String) aiConfigMap.get("provider"));
            aiConfig.setBaseUrl((String) aiConfigMap.get("baseUrl"));
            aiConfig.setApiKey((String) aiConfigMap.get("apiKey"));
            aiConfig.setModel((String) aiConfigMap.get("model"));
            
            // 调用AI生成脑洞
            String synopsis = aiWritingService.generateSynopsis(novelTitle, aiConfig);
            
            Map<String, Object> response = new HashMap<>();
            response.put("synopsis", synopsis);
            response.put("title", novelTitle);
            
            logger.info("生成脑洞成功，书名: {}", novelTitle);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("生成脑洞失败", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "生成脑洞失败");
            errorResponse.put("message", e.getMessage());
            
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}