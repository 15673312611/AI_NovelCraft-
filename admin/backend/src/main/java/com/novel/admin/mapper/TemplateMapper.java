package com.novel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.TemplateDTO;
import com.novel.admin.entity.Prompt;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface TemplateMapper extends BaseMapper<Prompt> {

    @Select("<script>" +
            "SELECT pt.id, pt.name, pt.category, pt.type, pt.content, pt.description, " +
            "pt.usage_count as usageCount, pt.is_active as isActive, pt.is_default as isDefault, " +
            "pt.sort_order as sortOrder, " +
            "pt.created_time as createdTime, pt.updated_time as updatedTime, " +
            "COUNT(DISTINCT ptf.id) as favoriteCount " +
            "FROM prompt_templates pt " +
            "LEFT JOIN prompt_template_favorites ptf ON pt.id = ptf.template_id " +
            "<where>" +
            "<if test='category != null and category != \"\"'>" +
            "AND pt.category = #{category}" +
            "</if>" +
            "<if test='type != null and type != \"\"'>" +
            "AND pt.type = #{type}" +
            "</if>" +
            "</where>" +
            "GROUP BY pt.id " +
            "ORDER BY pt.is_default DESC, pt.sort_order ASC, pt.created_time DESC" +
            "</script>")
    Page<TemplateDTO> selectTemplatePage(Page<TemplateDTO> page, 
                                         @Param("category") String category,
                                         @Param("type") String type);

    @Select("SELECT pt.id, pt.name, pt.category, pt.type, pt.content, pt.description, " +
            "pt.usage_count as usageCount, pt.is_active as isActive, pt.is_default as isDefault, " +
            "pt.sort_order as sortOrder, " +
            "pt.created_time as createdTime, pt.updated_time as updatedTime, " +
            "COUNT(DISTINCT ptf.id) as favoriteCount " +
            "FROM prompt_templates pt " +
            "LEFT JOIN prompt_template_favorites ptf ON pt.id = ptf.template_id " +
            "WHERE pt.id = #{id} " +
            "GROUP BY pt.id")
    TemplateDTO selectTemplateById(@Param("id") Long id);
    
    @Update("UPDATE prompt_templates SET usage_count = usage_count + 1 WHERE id = #{id}")
    void incrementUsageCount(@Param("id") Long id);
    
    @Select("SELECT COUNT(*) FROM prompt_template_favorites WHERE template_id = #{templateId}")
    Integer getFavoriteCount(@Param("templateId") Long templateId);
    
    @Select("SELECT user_id FROM prompt_template_favorites WHERE template_id = #{templateId}")
    List<Long> getFavoriteUserIds(@Param("templateId") Long templateId);

    @Update("UPDATE prompt_templates SET is_default = 0 WHERE category = #{category} AND is_default = 1")
    void clearDefaultByCategory(@Param("category") String category);
}
