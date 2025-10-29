package com.novel.mapper;

import com.novel.entity.AIConversation;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * AI对话历史Mapper
 */
@Mapper
public interface AIConversationMapper {

    /**
     * 根据小说ID获取对话历史（最近100条）
     */
    @Select("SELECT * FROM ai_conversation WHERE novel_id = #{novelId} " +
            "ORDER BY created_at DESC LIMIT 100")
    List<AIConversation> findByNovelId(@Param("novelId") Long novelId);

    /**
     * 根据文档ID获取对话历史
     */
    @Select("SELECT * FROM ai_conversation WHERE document_id = #{documentId} " +
            "ORDER BY created_at DESC")
    List<AIConversation> findByDocumentId(@Param("documentId") Long documentId);

    /**
     * 根据ID获取对话
     */
    @Select("SELECT * FROM ai_conversation WHERE id = #{id}")
    AIConversation findById(@Param("id") Long id);

    /**
     * 创建对话记录
     */
    @Insert("INSERT INTO ai_conversation (novel_id, document_id, generator_id, user_message, " +
            "assistant_message, context_data, word_count) " +
            "VALUES (#{novelId}, #{documentId}, #{generatorId}, #{userMessage}, " +
            "#{assistantMessage}, #{contextData}, #{wordCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AIConversation conversation);

    /**
     * 删除对话记录
     */
    @Delete("DELETE FROM ai_conversation WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /**
     * 清空小说的对话历史
     */
    @Delete("DELETE FROM ai_conversation WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
}

