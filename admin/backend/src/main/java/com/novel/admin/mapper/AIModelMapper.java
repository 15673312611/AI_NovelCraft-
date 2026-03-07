package com.novel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.admin.entity.AIModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AIModelMapper extends BaseMapper<AIModel> {

    @Select("SELECT * FROM ai_model ORDER BY sort_order ASC, id ASC")
    List<AIModel> selectAllOrdered();

    @Select("SELECT * FROM ai_model WHERE available = true ORDER BY sort_order ASC")
    List<AIModel> selectAvailable();

    @Select("SELECT * FROM ai_model WHERE model_id = #{modelId}")
    AIModel selectByModelId(@Param("modelId") String modelId);

    @Update("UPDATE ai_model SET is_default = false WHERE is_default = true")
    int clearDefaultModel();

    @Update("UPDATE ai_model SET is_default = true WHERE id = #{id}")
    int setDefaultModel(@Param("id") Long id);
}
