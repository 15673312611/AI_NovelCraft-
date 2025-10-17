package com.novel.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.domain.entity.AIModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AIModelRepository extends BaseMapper<AIModel> {
    @Select("SELECT * FROM ai_model WHERE available = true ORDER BY cost_per_1k ASC")
    List<AIModel> findByAvailableTrueOrderByCostPer1kAsc();
}

