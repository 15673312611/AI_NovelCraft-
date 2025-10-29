package com.novel.mapper;

import com.novel.entity.ReferenceFile;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 参考文件Mapper
 */
@Mapper
public interface ReferenceFileMapper {

    /**
     * 根据小说ID获取所有参考文件
     */
    @Select("SELECT * FROM reference_file WHERE novel_id = #{novelId} ORDER BY created_at DESC")
    List<ReferenceFile> findByNovelId(@Param("novelId") Long novelId);

    /**
     * 根据ID获取参考文件
     */
    @Select("SELECT * FROM reference_file WHERE id = #{id}")
    ReferenceFile findById(@Param("id") Long id);

    /**
     * 创建参考文件
     */
    @Insert("INSERT INTO reference_file (novel_id, file_name, file_type, file_content, " +
            "file_size, original_path, word_count) " +
            "VALUES (#{novelId}, #{fileName}, #{fileType}, #{fileContent}, #{fileSize}, " +
            "#{originalPath}, #{wordCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReferenceFile referenceFile);

    /**
     * 删除参考文件
     */
    @Delete("DELETE FROM reference_file WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /**
     * 根据ID列表批量查询
     */
    @Select("<script>" +
            "SELECT * FROM reference_file WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<ReferenceFile> findByIds(@Param("ids") List<Long> ids);
}

