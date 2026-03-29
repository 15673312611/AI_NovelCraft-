package com.novel.mapper;

import com.novel.entity.AIConversation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AIConversationMapper {

    @Select("SELECT id, novel_id, document_id, generator_id, assistant_message, word_count, created_at " +
            "FROM ai_conversation WHERE novel_id = #{novelId} ORDER BY created_at DESC LIMIT 100")
    List<AIConversation> findByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT id, novel_id, document_id, generator_id, assistant_message, word_count, created_at " +
            "FROM ai_conversation WHERE document_id = #{documentId} ORDER BY created_at DESC")
    List<AIConversation> findByDocumentId(@Param("documentId") Long documentId);

    @Select("SELECT * FROM ai_conversation WHERE id = #{id}")
    AIConversation findById(@Param("id") Long id);

    @Insert("INSERT INTO ai_conversation (novel_id, document_id, generator_id, user_message, " +
            "assistant_message, context_data, word_count) " +
            "VALUES (#{novelId}, #{documentId}, #{generatorId}, #{userMessage}, " +
            "#{assistantMessage}, #{contextData}, #{wordCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AIConversation conversation);

    @Delete("DELETE FROM ai_conversation WHERE id = #{id}")
    int delete(@Param("id") Long id);

    @Delete("DELETE FROM ai_conversation WHERE novel_id = #{novelId}")
    int deleteByNovelId(@Param("novelId") Long novelId);
}
