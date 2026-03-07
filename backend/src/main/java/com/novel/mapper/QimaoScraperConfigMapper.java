package com.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.entity.QimaoScraperConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 爬虫配置Mapper接口
 */
@Mapper
public interface QimaoScraperConfigMapper extends BaseMapper<QimaoScraperConfig> {
    
    /**
     * 根据配置键获取配置值
     */
    @Select("SELECT config_value FROM qimao_scraper_config WHERE config_key = #{configKey}")
    String getConfigValue(String configKey);
}
