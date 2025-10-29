package com.novel.service;

import com.novel.entity.NovelFolder;
import com.novel.mapper.NovelFolderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文件夹服务
 */
@Service
@Slf4j
public class NovelFolderService {

    @Autowired
    private NovelFolderMapper folderMapper;

    /**
     * 获取小说的所有文件夹
     */
    public List<NovelFolder> getFoldersByNovelId(Long novelId) {
        log.info("获取小说ID={}的所有文件夹", novelId);
        return folderMapper.findByNovelId(novelId);
    }

    /**
     * 获取小说的所有文件夹（带行锁）
     */
    public List<NovelFolder> getFoldersByNovelIdForUpdate(Long novelId) {
        log.info("获取小说ID={}的所有文件夹（加锁）", novelId);
        return folderMapper.findByNovelIdForUpdate(novelId);
    }

    /**
     * 根据ID获取文件夹
     */
    public NovelFolder getFolderById(Long id) {
        log.info("获取文件夹ID={}", id);
        return folderMapper.findById(id);
    }

    /**
     * 创建文件夹
     */
    @Transactional
    public NovelFolder createFolder(NovelFolder folder) {
        log.info("创建文件夹: {}", folder.getFolderName());
        
        // 设置默认值
        if (folder.getSortOrder() == null) {
            folder.setSortOrder(0);
        }
        
        int result = folderMapper.insert(folder);
        if (result > 0) {
            log.info("文件夹创建成功，ID={}", folder.getId());
            return folder;
        }
        throw new RuntimeException("文件夹创建失败");
    }

    /**
     * 更新文件夹
     */
    @Transactional
    public NovelFolder updateFolder(NovelFolder folder) {
        log.info("更新文件夹ID={}", folder.getId());
        
        // 检查是否为系统文件夹
        NovelFolder existing = folderMapper.findById(folder.getId());
        if (existing != null && Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new RuntimeException("系统文件夹不可重命名");
        }
        
        int result = folderMapper.update(folder);
        if (result > 0) {
            return folderMapper.findById(folder.getId());
        }
        throw new RuntimeException("文件夹更新失败");
    }

    /**
     * 删除文件夹
     */
    @Transactional
    public void deleteFolder(Long id) {
        log.info("删除文件夹ID={}", id);
        
        // 检查是否为系统文件夹
        NovelFolder folder = folderMapper.findById(id);
        if (folder != null && Boolean.TRUE.equals(folder.getIsSystem())) {
            throw new RuntimeException("系统文件夹不可删除");
        }
        
        int result = folderMapper.delete(id);
        if (result == 0) {
            throw new RuntimeException("文件夹删除失败");
        }
    }

    /**
     * 为新小说创建默认章节文件夹
     * 注意: 此方法已废弃，章节现在直接存储在 chapters 表
     */
    @Deprecated
    @Transactional
    public NovelFolder createDefaultChapterFolder(Long novelId) {
        log.info("为小说ID={}创建默认章节文件夹", novelId);
        NovelFolder folder = new NovelFolder();
        folder.setNovelId(novelId);
        folder.setFolderName("章节");
        folder.setSortOrder(0);
        return createFolder(folder);
    }

    /**
     * 根据父级和名称查找文件夹
     */
    public NovelFolder findFolderByParentAndName(Long novelId, Long parentId, String name) {
        List<NovelFolder> folders = folderMapper.findByNovelId(novelId);
        return folders.stream()
                .filter(folder -> {
                    Long pid = folder.getParentId();
                    if (parentId == null) {
                        return (pid == null) && folder.getFolderName().equals(name);
                    }
                    return parentId.equals(pid) && folder.getFolderName().equals(name);
                })
                .findFirst()
                .orElse(null);
    }

    /**
     * 如果不存在则创建文件夹
     */
    @Transactional
    public NovelFolder createFolderIfAbsent(Long novelId, Long parentId, String folderName, Integer sortOrder) {
        NovelFolder existing = findFolderByParentAndName(novelId, parentId, folderName);
        if (existing != null) {
            return existing;
        }
        NovelFolder folder = new NovelFolder();
        folder.setNovelId(novelId);
        folder.setParentId(parentId);
        folder.setFolderName(folderName);
        folder.setSortOrder(sortOrder != null ? sortOrder : 0);
        return createFolder(folder);
    }
}

