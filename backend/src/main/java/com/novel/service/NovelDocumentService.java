package com.novel.service;

import com.novel.entity.NovelDocument;
import com.novel.entity.NovelFolder;
import com.novel.mapper.NovelDocumentMapper;
import com.novel.service.NovelFolderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文档服务
 */
@Service
@Slf4j
public class NovelDocumentService {

    @Autowired
    private NovelDocumentMapper documentMapper;

    @Autowired
    private FileParserService fileParserService;

    @Autowired
    private NovelFolderService folderService;

    @Autowired
    private WritingVersionHistoryService writingVersionHistoryService;

    /**
     * 获取小说的所有文档
     */
    public List<NovelDocument> getDocumentsByNovelId(Long novelId) {
        log.info("获取小说ID={}的所有文档", novelId);
        return documentMapper.findByNovelId(novelId);
    }

    /**
     * 获取文件夹下的文档列表
     */
    public List<NovelDocument> getDocumentsByFolderId(Long folderId) {
        log.info("获取文件夹ID={}下的文档", folderId);
        return documentMapper.findByFolderId(folderId);
    }

    /**
     * 根据ID获取文档
     */
    public NovelDocument getDocumentById(Long id) {
        log.info("获取文档ID={}", id);
        NovelDocument document = documentMapper.findById(id);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }
        return document;
    }

    /**
     * 创建文档
     */
    @Transactional
    public NovelDocument createDocument(NovelDocument document) {
        log.info("创建文档: {}", document.getTitle());

        // 检查同文件夹下是否重名
        NovelDocument existing = documentMapper.findByFolderAndTitle(document.getFolderId(), document.getTitle());
        if (existing != null) {
            throw new RuntimeException("同一文件夹下已存在同名文档");
        }

        // 设置默认值
        if (document.getSortOrder() == null) {
            document.setSortOrder(0);
        }

        int result = documentMapper.insert(document);
        if (result > 0) {
            log.info("文档创建成功，ID={}", document.getId());
            return document;
        }
        throw new RuntimeException("文档创建失败");
    }


    /**
     * 更新文档
     */
    @Transactional
    public NovelDocument updateDocument(NovelDocument document) {
        log.info("更新文档ID={}", document.getId());

        int result = documentMapper.update(document);
        if (result > 0) {
            return documentMapper.findById(document.getId());
        }
        throw new RuntimeException("文档更新失败");
    }

    /**
     * 获取最近的章节（用于代理式AI写作）
     */
    public List<NovelDocument> getRecentChapters(Long novelId, Integer beforeChapter, Integer limit) {
        log.info("获取小说ID={}在第{}章之前的最近{}章", novelId, beforeChapter, limit);
        return documentMapper.findRecentChaptersByNovelId(novelId, beforeChapter, limit);
    }

    /**
     * 删除文档
     */
    @Transactional
    public void deleteDocument(Long id) {
        log.info("删除文档ID={}", id);
        int result = documentMapper.delete(id);
        if (result == 0) {
            throw new RuntimeException("文档删除失败");
        }
    }

    /**
     * 自动保存文档（只更新内容）
     */
    @Transactional
    public void autoSaveDocument(Long id, String content) {
        log.info("自动保存文档ID={}", id);
        // 读取旧内容用于版本历史记录
        NovelDocument existing = documentMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("文档不存在");
        }
        String previousContent = existing.getContent() != null ? existing.getContent() : "";
        existing.setContent(content);
        documentMapper.update(existing);
        try {
            // 辅助文档的版本历史（可选），来源统一标记为 AUTO_SAVE
            writingVersionHistoryService.recordDocumentVersion(
                    existing,
                    previousContent,
                    content,
                    "AUTO_SAVE"
            );
        } catch (Exception e) {
            log.warn("记录文档版本历史失败（不影响自动保存）: {}", e.getMessage());
        }
    }

    /**
     * 搜索文档
     */
    public List<NovelDocument> searchDocuments(Long novelId, String keyword) {
        log.info("搜索小说ID={}的文档，关键词={}", novelId, keyword);
        return documentMapper.searchDocuments(novelId, keyword);
    }

    /**
     * 初始化默认文件夹结构（仅创建辅助文档文件夹）
     * 章节相关内容使用 chapters 表管理
     * 使用 SELECT FOR UPDATE 实现悲观锁，防止并发创建
     */
    @Transactional
    public void initDefaultFolders(Long novelId) {
        log.info("初始化小说ID={}的默认文件夹结构（辅助文档）", novelId);

        try {
            // 使用 FOR UPDATE 锁定该小说的所有文件夹记录
            List<NovelFolder> existingFolders = folderService.getFoldersByNovelIdForUpdate(novelId);
            
            // 检查是否已有"设定"文件夹（作为初始化完成的标记）
            boolean hasSettingsFolder = existingFolders.stream()
                    .anyMatch(f -> "设定".equals(f.getFolderName()) && f.getParentId() == null);
            
            if (hasSettingsFolder) {
                log.info("小说ID={}的辅助文档文件夹已初始化，跳过", novelId);
                return;
            }

            // 到这里说明拿到了锁且没有初始化，开始创建辅助文档文件夹
            log.info("开始创建辅助文档文件夹结构...");

            // 1. 创建"设定"文件夹
            NovelFolder settings = new NovelFolder();
            settings.setNovelId(novelId);
            settings.setFolderName("设定");
            settings.setParentId(null);
            settings.setSortOrder(0);
            settings.setIsSystem(true);
            folderService.createFolder(settings);

            // 2. 创建"角色"文件夹
            NovelFolder characters = new NovelFolder();
            characters.setNovelId(novelId);
            characters.setFolderName("角色");
            characters.setParentId(null);
            characters.setSortOrder(1);
            characters.setIsSystem(true);
            folderService.createFolder(characters);

            // 3. 创建"知识库"文件夹
            NovelFolder knowledge = new NovelFolder();
            knowledge.setNovelId(novelId);
            knowledge.setFolderName("知识库");
            knowledge.setParentId(null);
            knowledge.setSortOrder(2);
            knowledge.setIsSystem(true);
            folderService.createFolder(knowledge);

            log.info("辅助文档文件夹结构初始化完成");
        } catch (Exception e) {
            log.error("初始化默认文件夹失败", e);
            // 如果是并发导致的重复创建，不抛出异常
            if (e.getMessage() != null && e.getMessage().contains("已存在")) {
                log.warn("检测到并发创建，忽略错误");
                return;
            }
            throw new RuntimeException("初始化默认文件夹失败: " + e.getMessage(), e);
        }
    }
}

