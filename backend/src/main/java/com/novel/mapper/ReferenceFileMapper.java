package com.novel.mapper;

import com.novel.entity.ReferenceFile;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReferenceFileMapper {

    @Select("SELECT id, novel_id, file_name, file_type, file_size, word_count, created_at, updated_at " +
            "FROM reference_file WHERE novel_id = #{novelId} ORDER BY created_at DESC")
    List<ReferenceFile> findByNovelId(@Param("novelId") Long novelId);

    @Select("SELECT * FROM reference_file WHERE id = #{id}")
    ReferenceFile findById(@Param("id") Long id);

    @Insert("INSERT INTO reference_file (novel_id, file_name, file_type, file_content, " +
            "file_size, original_path, word_count) " +
            "VALUES (#{novelId}, #{fileName}, #{fileType}, #{fileContent}, #{fileSize}, " +
            "#{originalPath}, #{wordCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ReferenceFile referenceFile);

    @Delete("DELETE FROM reference_file WHERE id = #{id}")
    int delete(@Param("id") Long id);

    @Select("<script>" +
            "SELECT * FROM reference_file WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<ReferenceFile> findByIds(@Param("ids") List<Long> ids);
}
