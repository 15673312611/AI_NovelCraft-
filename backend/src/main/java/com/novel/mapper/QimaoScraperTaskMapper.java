package com.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.entity.QimaoScraperTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬虫任务Mapper接口
 */
@Mapper
public interface QimaoScraperTaskMapper extends BaseMapper<QimaoScraperTask> {
}
