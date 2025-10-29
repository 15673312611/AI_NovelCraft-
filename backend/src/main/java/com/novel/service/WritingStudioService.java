package com.novel.service;

import com.novel.domain.entity.Chapter;
import com.novel.entity.NovelFolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 写作工作室服务
 * 统一管理写作工作室的初始化和数据结构
 */
@Service
@Slf4j
public class WritingStudioService {

    @Autowired
    private NovelFolderService folderService;

    @Autowired
    private NovelDocumentService documentService;

    @Autowired
    private ChapterService chapterService;

    /**
     * 初始化写作工作室
     * 1. 创建第一章到 chapters 表
     * 2. 创建辅助文档文件夹（设定、角色、知识库）
     * 注意："主要内容"是前端虚拟节点，不存数据库
     */
    @Transactional
    public void initWritingStudio(Long novelId) {
        log.info("初始化写作工作室，小说ID={}", novelId);

        try {
            // 使用 FOR UPDATE 锁定该小说的所有文件夹记录
            List<NovelFolder> existingFolders = folderService.getFoldersByNovelIdForUpdate(novelId);

            // 检查是否已初始化（通过"设定"文件夹判断）
            boolean hasSettings = existingFolders.stream()
                    .anyMatch(f -> "设定".equals(f.getFolderName()) && f.getParentId() == null);

            if (hasSettings) {
                log.info("小说ID={}的写作工作室已初始化，跳过", novelId);
                return;
            }

            log.info("开始初始化写作工作室...");

            // 1. 创建第一章到 chapters 表
            Chapter firstChapter = chapterService.initFirstChapter(novelId);
            log.info("创建第一章成功，章节ID={}", firstChapter.getId());

            // 2. 创建辅助文档文件夹
            documentService.initDefaultFolders(novelId);
            log.info("创建辅助文档文件夹成功");

            log.info("写作工作室初始化完成，小说ID={}", novelId);

        } catch (Exception e) {
            log.error("初始化写作工作室失败", e);
            // 如果是并发导致的重复创建，不抛出异常
            if (e.getMessage() != null && e.getMessage().contains("已存在")) {
                log.warn("检测到并发创建，忽略错误");
                return;
            }
            throw new RuntimeException("初始化写作工作室失败: " + e.getMessage(), e);
        }
    }
}

