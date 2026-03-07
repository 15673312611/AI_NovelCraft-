package com.novel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novel.domain.entity.CreditPackage;
import com.novel.mapper.CreditPackageMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CreditPackageService extends ServiceImpl<CreditPackageMapper, CreditPackage> {

    /**
     * 获取所有启用套餐（按排序）
     */
    public List<CreditPackage> getActivePackages() {
        return list(new LambdaQueryWrapper<CreditPackage>()
                .eq(CreditPackage::getIsActive, true)
                .orderByAsc(CreditPackage::getSortOrder)
                .orderByAsc(CreditPackage::getPrice));
    }

    /**
     * 获取所有套餐（后台用，按排序）
     */
    public List<CreditPackage> getAllPackages() {
        return list(new LambdaQueryWrapper<CreditPackage>()
                .orderByAsc(CreditPackage::getSortOrder)
                .orderByAsc(CreditPackage::getPrice));
    }
}