package com.novel.service;

import com.novel.entity.AIConversation;
import com.novel.mapper.AIConversationMapper;
import com.novel.repository.NovelRepository;
import com.novel.domain.entity.Novel;
import com.novel.common.security.AuthUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI对话历史服务
 */
@Service
@Slf4j
public class AIConversationService {

    @Autowired
    private AIConversationMapper conversationMapper;

    @Autowired
    private FileParserService fileParserService;

    @Autowired
    private NovelRepository novelRepository;

    /**
     * 获取小说的对话历史
     */
    public List<AIConversation> getConversationsByNovelId(Long novelId) {
        log.info("获取小说ID={}的对话历史", novelId);
        return conversationMapper.findByNovelId(novelId);
    }

    /**
     * 获取文档的对话历史
     */
    public List<AIConversation> getConversationsByDocumentId(Long documentId) {
        log.info("获取文档ID={}的对话历史", documentId);
        return conversationMapper.findByDocumentId(documentId);
    }

    /**
     * 根据ID获取对话
     */
    public AIConversation getConversationById(Long id) {
        log.info("获取对话ID={}", id);
        return conversationMapper.findById(id);
    }

    /**
     * 保存对话记录
     */
    @Transactional
    public AIConversation saveConversation(AIConversation conversation) {
        log.info("保存AI对话记录");
        
        // 计算生成字数
        if (conversation.getAssistantMessage() != null) {
            int wordCount = fileParserService.countWords(conversation.getAssistantMessage());
            conversation.setWordCount(wordCount);
        } else {
            conversation.setWordCount(0);
        }
        
        int result = conversationMapper.insert(conversation);
        if (result > 0) {
            log.info("对话记录保存成功，ID={}", conversation.getId());
            return conversation;
        }
        throw new RuntimeException("对话记录保存失败");
    }

    /**
     * 删除对话记录
     */
    @Transactional
    public void deleteConversation(Long id) {
        log.info("删除对话记录ID={}", id);

        Long currentUserId = AuthUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("用户未登录，无法删除对话记录");
        }

        AIConversation conversation = conversationMapper.findById(id);
        if (conversation == null) {
            throw new RuntimeException("对话记录不存在");
        }

        Novel novel = novelRepository.selectById(conversation.getNovelId());
        if (novel == null || novel.getAuthorId() == null || !currentUserId.equals(novel.getAuthorId())) {
            throw new SecurityException("无权限删除该对话记录");
        }

        int result = conversationMapper.delete(id);
        if (result == 0) {
            throw new RuntimeException("对话记录删除失败");
        }
    }

    /**
     * 清空小说的对话历史
     */
    @Transactional
    public void clearConversationsByNovelId(Long novelId) {
        log.info("清空小说ID={}的对话历史", novelId);
        conversationMapper.deleteByNovelId(novelId);
    }
    /**
     * 清空当前用户某本小说的对话历史（带权限校验）
     */
    @Transactional
    public void clearConversationsByNovelIdForCurrentUser(Long novelId) {
        Long currentUserId = AuthUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new SecurityException("用户未登录，无法清空对话历史");
        }

        Novel novel = novelRepository.selectById(novelId);
        if (novel == null || novel.getAuthorId() == null || !currentUserId.equals(novel.getAuthorId())) {
            throw new SecurityException("无权限清空该小说的对话历史");
        }

        clearConversationsByNovelId(novelId);
    }
}
