package com.novel.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.NovelOutline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

@Mapper
public interface NovelOutlineRepository extends BaseMapper<NovelOutline> {
    // 单条查找：根据 novel_id 取最新的一条（MP 默认表 novel_outlines）
    default Optional<NovelOutline> findByNovelId(Long novelId) {
        QueryWrapper<NovelOutline> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelId);
        queryWrapper.orderByDesc("updated_at"); // 按更新时间降序，取最新的
        queryWrapper.last("LIMIT 1"); // 只取一条
        java.util.List<NovelOutline> outlines = selectList(queryWrapper);
        if (outlines != null && !outlines.isEmpty()) {
            return Optional.of(outlines.get(0));
        }
        return Optional.empty();
    }

    // 单条查找：根据 novel_id 和 status
    default Optional<NovelOutline> findByNovelIdAndStatus(Long novelId, NovelOutline.OutlineStatus status) {
        QueryWrapper<NovelOutline> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("novel_id", novelId);
//        queryWrapper.eq("status", status);
        queryWrapper.orderByDesc("updated_at"); // 取最新的
        queryWrapper.last("LIMIT 1");
        NovelOutline outline = selectOne(queryWrapper);
        return Optional.ofNullable(outline);
    }

    // 列表查找：按状态
    default List<NovelOutline> findByStatus(NovelOutline.OutlineStatus status) {
        QueryWrapper<NovelOutline> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", status);
        return selectList(queryWrapper);
    }

    // 列表查找：需要审核的大纲
    default List<NovelOutline> findOutlinesForReview() {
        QueryWrapper<NovelOutline> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", NovelOutline.OutlineStatus.DRAFT);
        return selectList(queryWrapper);
    }

    // 分页查找：根据 novel_id
    @Select("SELECT * FROM novel_outlines WHERE novel_id = #{novelId}")
    IPage<NovelOutline> findPagedByNovelId(@Param("novelId") Long novelId, Page<NovelOutline> page);
}

