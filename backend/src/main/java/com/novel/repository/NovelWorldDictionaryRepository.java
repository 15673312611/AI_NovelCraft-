package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.NovelWorldDictionary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NovelWorldDictionaryRepository extends BaseMapper<NovelWorldDictionary> {
    List<NovelWorldDictionary> findByNovelId(@Param("novelId") Long novelId);
    List<NovelWorldDictionary> findByNovelIdAndType(@Param("novelId") Long novelId, @Param("type") String type);
    List<NovelWorldDictionary> findByNovelIdAndImportant(@Param("novelId") Long novelId, @Param("isImportant") Boolean isImportant);
    NovelWorldDictionary findByNovelIdAndTerm(@Param("novelId") Long novelId, @Param("term") String term);
    List<NovelWorldDictionary> findByNovelIdAndFirstMention(@Param("novelId") Long novelId, @Param("firstMention") Integer firstMention);
}

