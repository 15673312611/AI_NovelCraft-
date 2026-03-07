package com.novel.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novel.admin.entity.CreditPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 充值套餐Mapper
 */
@Mapper
public interface CreditPackageMapper extends BaseMapper<CreditPackage> {

    /**
     * 获取所有启用的套餐
     */
    @Select("SELECT * FROM credit_packages WHERE is_active = 1 ORDER BY sort_order ASC, id ASC")
    List<CreditPackage> selectActivePackages();

    /**
     * 获取所有套餐（按排序）
     */
    @Select("SELECT * FROM credit_packages ORDER BY sort_order ASC, id ASC")
    List<CreditPackage> selectAllOrdered();
}
