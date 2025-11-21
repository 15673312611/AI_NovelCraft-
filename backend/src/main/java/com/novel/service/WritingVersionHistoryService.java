package com.novel.service;

import com.novel.domain.entity.Chapter;
import com.novel.entity.NovelDocument;
import com.novel.entity.WritingVersionHistory;
import com.novel.mapper.WritingVersionHistoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 写作版本历史服务
 *
 * 负责根据内容变化和时间间隔，智能决定是否记录一个版本快照，
 * 避免自动保存过于频繁地向历史表刷数据。
 */
@Service
@Slf4j
public class WritingVersionHistoryService {

    @Autowired
    private WritingVersionHistoryMapper historyMapper;

    @Autowired
    private FileParserService fileParserService;

    /**
     * 为章节记录一个版本（自动判断是否应该落库）
     *
     * @param chapter       当前章节实体（已更新内容）
     * @param previousContent 更新前的正文内容
     * @param newContent      更新后的正文内容
     * @param sourceType      版本来源（AUTO_SAVE / MANUAL_SAVE / AI_REPLACE 等）
     */
    public void recordChapterVersion(Chapter chapter,
                                     String previousContent,
                                     String newContent,
                                     String sourceType) {
        if (chapter == null) {
            return;
        }
        Long chapterId = chapter.getId();
        if (chapterId == null) {
            return;
        }

        String normalizedNew = newContent != null ? newContent : "";
        String normalizedPrev = previousContent != null ? previousContent : "";

        // 基础：内容为空或过短，不记录版本
        int wordCount = fileParserService.countWords(normalizedNew);
        if (wordCount < 200) {
            // 正文很短时，频繁记录意义不大
            log.debug("章节 {} 内容字数({})较少，跳过版本记录", chapterId, wordCount);
            return;
        }

        // 查询最近一条记录，用于时间/差异双重判断
        WritingVersionHistory last = historyMapper.findLatestByChapterId(chapterId);

        // 如果之前没有任何版本，直接记录一版作为起点
        if (last == null) {
            WritingVersionHistory history = new WritingVersionHistory();
            history.setNovelId(chapter.getNovelId());
            history.setChapterId(chapterId);
            history.setDocumentId(null);
            history.setSourceType(sourceType);
            history.setContent(normalizedNew);
            history.setWordCount(wordCount);
            // 第一个版本没有“前一版”可比，对应 diffRatio 置为 null
            history.setDiffRatio(null);
            historyMapper.insert(history);
            // 只保留最近10条
            safeTrimChapterHistory(chapterId, 10);
            log.info("已为章节 {} 记录首个版本历史（字数={}）", chapterId, wordCount);
            return;
        }

        String baseContent = last.getContent() != null ? last.getContent() : normalizedPrev;
        if (baseContent == null) {
            baseContent = "";
        }

        double similarity = calculateSimilarity(baseContent, normalizedNew);
        double diffRatio = (1.0 - similarity) * 100.0;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastCreatedAt = last.getCreatedAt();
        long secondsSinceLast = lastCreatedAt != null ? Duration.between(lastCreatedAt, now).getSeconds() : Long.MAX_VALUE;

        // 规则：
        // - 1 分钟内变动 < 30%：不记新版本（小改动）
        // - 1~5 分钟内变动 < 10%：也不记（频繁小改）
        // - 其余情况：视为一次较大的修改，记录版本
        if (secondsSinceLast < 60 && diffRatio < 30.0) {
            log.debug("章节 {} 距离上次版本仅 {} 秒，差异约 {:.2f}% ，跳过记录",
                    chapterId, secondsSinceLast, diffRatio);
            return;
        }
        if (secondsSinceLast < 300 && diffRatio < 10.0) {
            log.debug("章节 {} 5 分钟内小幅修改（差异约 {:.2f}%），跳过记录", chapterId, diffRatio);
            return;
        }

        WritingVersionHistory history = new WritingVersionHistory();
        history.setNovelId(chapter.getNovelId());
        history.setChapterId(chapterId);
        history.setDocumentId(null);
        history.setSourceType(sourceType);
        history.setContent(normalizedNew);
        history.setWordCount(wordCount);
        history.setDiffRatio(diffRatio);
        historyMapper.insert(history);
        // 只保留最近10条
        safeTrimChapterHistory(chapterId, 10);

        log.info("已记录章节 {} 的版本历史，来源={}，字数={}，差异约 {:.2f}% ，距上次 {} 秒",
                chapterId, sourceType, wordCount, diffRatio, secondsSinceLast);
    }

    /**
     * 为辅助文档记录一个版本（逻辑与章节类似，当前未强制使用）
     */
    public void recordDocumentVersion(NovelDocument document,
                                      String previousContent,
                                      String newContent,
                                      String sourceType) {
        if (document == null || document.getId() == null) {
            return;
        }
        Long documentId = document.getId();

        String normalizedNew = newContent != null ? newContent : "";
        String normalizedPrev = previousContent != null ? previousContent : "";

        int wordCount = fileParserService.countWords(normalizedNew);
        if (wordCount < 200) {
            log.debug("文档 {} 内容字数({})较少，跳过版本记录", documentId, wordCount);
            return;
        }

        WritingVersionHistory last = historyMapper.findLatestByDocumentId(documentId);
        if (last == null) {
            WritingVersionHistory history = new WritingVersionHistory();
            history.setNovelId(document.getNovelId());
            history.setChapterId(null);
            history.setDocumentId(documentId);
            history.setSourceType(sourceType);
            history.setContent(normalizedNew);
            history.setWordCount(wordCount);
            history.setDiffRatio(null);
            historyMapper.insert(history);
            safeTrimDocumentHistory(documentId, 10);
            log.info("已为文档 {} 记录首个版本历史（字数={}）", documentId, wordCount);
            return;
        }

        String baseContent = last.getContent() != null ? last.getContent() : normalizedPrev;
        if (baseContent == null) {
            baseContent = "";
        }

        double similarity = calculateSimilarity(baseContent, normalizedNew);
        double diffRatio = (1.0 - similarity) * 100.0;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastCreatedAt = last.getCreatedAt();
        long secondsSinceLast = lastCreatedAt != null ? Duration.between(lastCreatedAt, now).getSeconds() : Long.MAX_VALUE;

        if (secondsSinceLast < 60 && diffRatio < 30.0) {
            log.debug("文档 {} 距离上次版本仅 {} 秒，差异约 {:.2f}% ，跳过记录",
                    documentId, secondsSinceLast, diffRatio);
            return;
        }
        if (secondsSinceLast < 300 && diffRatio < 10.0) {
            log.debug("文档 {} 5 分钟内小幅修改（差异约 {:.2f}%），跳过记录", documentId, diffRatio);
            return;
        }

        WritingVersionHistory history = new WritingVersionHistory();
        history.setNovelId(document.getNovelId());
        history.setChapterId(null);
        history.setDocumentId(documentId);
        history.setSourceType(sourceType);
        history.setContent(normalizedNew);
        history.setWordCount(wordCount);
        history.setDiffRatio(diffRatio);
        historyMapper.insert(history);
        safeTrimDocumentHistory(documentId, 10);

        log.info("已记录文档 {} 的版本历史，来源={}，字数={}，差异约 {:.2f}% ，距上次 {} 秒",
                documentId, sourceType, wordCount, diffRatio, secondsSinceLast);
    }

    /**
     * 获取章节的版本历史（按时间倒序，限制条数）
     */
    public List<WritingVersionHistory> getChapterHistory(Long chapterId, int limit) {
        int queryLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        return historyMapper.findByChapterId(chapterId, queryLimit);
    }

    /**
     * 获取文档的版本历史（按时间倒序，限制条数）
     */
    public List<WritingVersionHistory> getDocumentHistory(Long documentId, int limit) {
        int queryLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        return historyMapper.findByDocumentId(documentId, queryLimit);
    }

    /**
     * 计算两个字符串的相似度（与 ChapterService 中的简化算法保持一致）
     */
    private double calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;
        if (s1.equals(s2)) return 1.0;

        int maxLen = Math.max(s1.length(), s2.length());
        if (maxLen == 0) return 1.0;

        int sampleLen = Math.min(1000, Math.min(s1.length(), s2.length()));
        if (sampleLen == 0) return 0.0;

        String sample1 = s1.substring(0, sampleLen);
        String sample2 = s2.substring(0, sampleLen);

        int matches = 0;
        for (int i = 0; i < sampleLen; i++) {
            if (sample1.charAt(i) == sample2.charAt(i)) {
                matches++;
            }
        }

        double lengthSimilarity = 1.0 - Math.abs(s1.length() - s2.length()) / (double) maxLen;
        double contentSimilarity = matches / (double) sampleLen;

        return (lengthSimilarity + contentSimilarity) / 2.0;
    }

    private void safeTrimChapterHistory(Long chapterId, int keep) {
        try {
            historyMapper.deleteOldByChapterId(chapterId, keep);
        } catch (Exception e) {
            log.warn("清理章节 {} 历史版本失败（不影响写入）: {}", chapterId, e.getMessage());
        }
    }

    private void safeTrimDocumentHistory(Long documentId, int keep) {
        try {
            historyMapper.deleteOldByDocumentId(documentId, keep);
        } catch (Exception e) {
            log.warn("清理文档 {} 历史版本失败（不影响写入）: {}", documentId, e.getMessage());
        }
    }
}
