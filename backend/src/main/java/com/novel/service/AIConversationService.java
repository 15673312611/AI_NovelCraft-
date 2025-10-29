package com.novel.service;

import com.novel.entity.AIConversation;
import com.novel.mapper.AIConversationMapper;
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
}

