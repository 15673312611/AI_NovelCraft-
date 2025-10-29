package com.novel.mapper;

import com.novel.entity.NovelFolder;
import org.apache.ibatis.annotations.*;
import java.util.List;

/**
 * 文件夹Mapper
 */
@Mapper
public interface NovelFolderMapper {

    /**
     * 根据小说ID获取所有文件夹
     */
    @Select("SELECT * FROM novel_folder WHERE novel_id = #{novelId} ORDER BY sort_order ASC, id ASC")
    List<NovelFolder> findByNovelId(@Param("novelId") Long novelId);

    /**
     * 根据小说ID获取所有文件夹（带行锁，用于防止并发）
     */
    @Select("SELECT * FROM novel_folder WHERE novel_id = #{novelId} FOR UPDATE")
    List<NovelFolder> findByNovelIdForUpdate(@Param("novelId") Long novelId);

    /**
     * 根据ID获取文件夹
     */
    @Select("SELECT * FROM novel_folder WHERE id = #{id}")
    NovelFolder findById(@Param("id") Long id);

    /**
     * 创建文件夹
     */
    @Insert("INSERT INTO novel_folder (novel_id, folder_name, parent_id, sort_order, is_system) " +
            "VALUES (#{novelId}, #{folderName}, #{parentId}, #{sortOrder}, #{isSystem})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NovelFolder folder);

    /**
     * 更新文件夹
     */
    @Update("UPDATE novel_folder SET folder_name = #{folderName}, sort_order = #{sortOrder} " +
            "WHERE id = #{id}")
    int update(NovelFolder folder);

    /**
     * 删除文件夹
     */
    @Delete("DELETE FROM novel_folder WHERE id = #{id}")
    int delete(@Param("id") Long id);

    /**
     * 批量更新排序
     */
    @Update("<script>" +
            "UPDATE novel_folder SET sort_order = #{sortOrder} WHERE id = #{id}" +
            "</script>")
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);
}

