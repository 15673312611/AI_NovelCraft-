package com.novel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novel.domain.entity.PromptTemplate;
import com.novel.domain.entity.PromptTemplateFavorite;
import com.novel.common.security.AuthUtils;
import com.novel.repository.PromptTemplateRepository;
import com.novel.repository.PromptTemplateFavoriteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 閹绘劗銇氱拠宥喣侀弶鎸庢箛閸?
 */
@Service
public class PromptTemplateService extends ServiceImpl<PromptTemplateRepository, PromptTemplate> {

    private static final Logger logger = LoggerFactory.getLogger(PromptTemplateService.class);

    @Autowired
    private PromptTemplateFavoriteRepository favoriteRepository;


    /**
     * 娴犲懘鈧瀚ㄩ崚妤勩€冪仦鏇犮仛閹碘偓闂団偓鐎涙顔岄敍宀勪缉閸忓秷绻戦崶鐐村絹缁€楦跨槤閸愬懎顔愮粵澶嬫櫛閹扮喍淇婇幁?
     */
    private void applyListFieldSelection(LambdaQueryWrapper<PromptTemplate> wrapper) {
        wrapper.select(
            PromptTemplate::getId,
            PromptTemplate::getName,
            PromptTemplate::getType,
            PromptTemplate::getUserId,
            PromptTemplate::getCategory,
            PromptTemplate::getDescription,
            PromptTemplate::getIsActive,
            PromptTemplate::getIsDefault,
            PromptTemplate::getSortOrder,
            PromptTemplate::getUsageCount,
            PromptTemplate::getCreatedTime,
            PromptTemplate::getUpdatedTime
        );
    }


    /**
     * 閺嶈宓両D閼惧嘲褰囧Ο鈩冩緲閸愬懎顔?
     */
    public String getTemplateContent(Long templateId) {
        if (templateId == null) {
            return null;
        }
        
        PromptTemplate template = getById(templateId);
        if (template == null || !template.getIsActive()) {
            logger.warn("模板不存在或已禁用: {}", templateId);
            return null;
        }

        // 閼奉亜鐣炬稊澶嬆侀弶澶哥矌閸忎浇顔忓Ο鈩冩緲閹碘偓鐏炵偟鏁ら幋铚傚▏閻?
        if ("custom".equals(template.getType())) {
            Long currentUserId = AuthUtils.getCurrentUserId();
            if (currentUserId == null || template.getUserId() == null || !template.getUserId().equals(currentUserId)) {
                logger.warn("无权使用他人自定义模板: templateId={}, userId={}", templateId, currentUserId);
                return null;
            }
        }
        
        // 婢х偛濮炴担璺ㄦ暏濞嗏剝鏆?
        template.setUsageCount(template.getUsageCount() + 1);
        updateById(template);
        
        return template.getContent();
    }

    /**
     * 閸掓稑缂撻悽銊﹀煕閼奉亜鐣炬稊澶嬆侀弶?
     */
    public PromptTemplate createCustomTemplate(Long userId, String name, String content, String category, String description) {

        PromptTemplate template = new PromptTemplate();
        template.setName(name);
        template.setContent(content);
        template.setType("custom");
        template.setUserId(userId);
        template.setCategory("chapter");
        template.setDescription(description);
        template.setIsActive(true);
        template.setUsageCount(0);
        template.setCreatedTime(LocalDateTime.now());
        template.setUpdatedTime(LocalDateTime.now());

        save(template);
        logger.info("用户创建自定义模板: userId={}, templateId={}", userId, template.getId());

        return template;
    }

    /**
     * 閺囧瓨鏌婇悽銊﹀煕閼奉亜鐣炬稊澶嬆侀弶?
     */
    public boolean updateCustomTemplate(Long templateId, Long userId, String name, String content, String category, String description) {
        PromptTemplate template = getById(templateId);
        if (template == null) {
            logger.warn("模板不存在: {}", templateId);
            return false;
        }
        
        // 閸欘亣鍏樻穱顔芥暭閼奉亜绻侀惃鍕侀弶?
        if (!"custom".equals(template.getType()) || !userId.equals(template.getUserId())) {
            logger.warn("无权修改该模板: templateId={}, userId={}", templateId, userId);
            return false;
        }
        
        template.setName(name);
        template.setContent(content);
        template.setCategory("chapter");
        template.setDescription(description);
        
        return updateById(template);
    }

    /**
     * 閸掔娀娅庨悽銊﹀煕閼奉亜鐣炬稊澶嬆侀弶?
     */
    public boolean deleteCustomTemplate(Long templateId, Long userId) {
        PromptTemplate template = getById(templateId);
        if (template == null) {
            return false;
        }
        
        // 閸欘亣鍏橀崚鐘绘珟閼奉亜绻侀惃鍕侀弶?
        if (!"custom".equals(template.getType()) || !userId.equals(template.getUserId())) {
            logger.warn("无权删除该模板: templateId={}, userId={}", templateId, userId);
            return false;
        }
        
        return removeById(templateId);
    }


    /**
     * 閼惧嘲褰囬崗顒€绱戝Ο鈩冩緲閸掓銆冮敍鍫濈暭閺傝膩閺夊尅绱?
     */
    public List<PromptTemplate> getPublicTemplates(Long userId, String category) {
        logger.info("Service层: 开始查询公开模板, userId={}, category={}", userId, category);
        
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        applyListFieldSelection(wrapper);
        wrapper.eq(PromptTemplate::getType, "official")
               .eq(PromptTemplate::getIsActive, true);
        
        if (category != null && !category.isEmpty()) {
            logger.info("按分类过滤模板: {}", category);
            wrapper.eq(PromptTemplate::getCategory, category);
        }
        
        wrapper.orderByDesc(PromptTemplate::getIsDefault)
               .orderByAsc(PromptTemplate::getSortOrder)
               .orderByDesc(PromptTemplate::getUsageCount)
               .orderByDesc(PromptTemplate::getCreatedTime);
        
        List<PromptTemplate> templates = list(wrapper);
        logger.info("获取模板成功: 数量={}", templates.size());
        
        // 閺嶅洩顔囬弨鎯版閻樿埖鈧?
        if (userId != null) {
            markFavoriteStatus(templates, userId);
        }
        
        return templates;
    }

    /**
     * 閼惧嘲褰囬悽銊﹀煕閼奉亜鐣炬稊澶嬆侀弶鍨灙鐞?
     */
    public List<PromptTemplate> getUserCustomTemplates(Long userId, String category) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        applyListFieldSelection(wrapper);
        wrapper.eq(PromptTemplate::getType, "custom")
               .eq(PromptTemplate::getUserId, userId)
               .eq(PromptTemplate::getIsActive, true);

        if (category != null && !category.isEmpty()) {
            wrapper.eq(PromptTemplate::getCategory, category);
        }

        wrapper.orderByDesc(PromptTemplate::getCreatedTime);
        
        List<PromptTemplate> templates = list(wrapper);
        
        // 閺嶅洩顔囬弨鎯版閻樿埖鈧?
        markFavoriteStatus(templates, userId);
        
        return templates;
    }

    /**
     * 閼惧嘲褰囬悽銊﹀煕閺€鎯版閻ㄥ嫭膩閺夊灝鍨悰?
     */
    public List<PromptTemplate> getUserFavoriteTemplates(Long userId, String category) {
        // 閺屻儴顕楅悽銊﹀煕閺€鎯版閻ㄥ嫭膩閺夌竸D閸掓銆?
        LambdaQueryWrapper<PromptTemplateFavorite> favoriteWrapper = new LambdaQueryWrapper<>();
        favoriteWrapper.eq(PromptTemplateFavorite::getUserId, userId);
        
        List<PromptTemplateFavorite> favorites = favoriteRepository.selectList(favoriteWrapper);
        if (favorites.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<Long> templateIds = favorites.stream()
            .map(PromptTemplateFavorite::getTemplateId)
            .collect(Collectors.toList());
        
        // 閺屻儴顕楀Ο鈩冩緲鐠囷附鍎?
        LambdaQueryWrapper<PromptTemplate> templateWrapper = new LambdaQueryWrapper<>();
        applyListFieldSelection(templateWrapper);
        templateWrapper.in(PromptTemplate::getId, templateIds)
                      .eq(PromptTemplate::getIsActive, true)
                      .and(w -> w.eq(PromptTemplate::getType, "official")
                                .or()
                                .eq(PromptTemplate::getUserId, userId));

        if (category != null && !category.isEmpty()) {
            templateWrapper.eq(PromptTemplate::getCategory, category);
        }
        
        List<PromptTemplate> templates = list(templateWrapper);
        
        // 閺€鎯版閸掓銆冩稉顓犳畱閹碘偓閺堝膩閺夊潡鍏橀弽鍥唶娑撳搫鍑￠弨鎯版
        templates.forEach(template -> template.setIsFavorited(true));
        
        return templates;
    }

    /**
     * 閺€鎯版濡剝婢?
     */
    public boolean favoriteTemplate(Long userId, Long templateId) {
        // 濡偓閺屻儲膩閺夋寧妲搁崥锕€鐡ㄩ崷?
        PromptTemplate template = getById(templateId);
        if (template == null || !template.getIsActive()) {
            logger.warn("模板不存在或已禁用: templateId={}", templateId);
            return false;
        }

        // 缁備焦顒涢弨鎯版娴犳牔姹夐懛顏勭暰娑斿膩閺?
        if ("custom".equals(template.getType()) && (template.getUserId() == null || !template.getUserId().equals(userId))) {
            logger.warn("无权收藏他人自定义模板: userId={}, templateId={}", userId, templateId);
            return false;
        }
        
        // 濡偓閺屻儲妲搁崥锕€鍑￠弨鎯版
        LambdaQueryWrapper<PromptTemplateFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplateFavorite::getUserId, userId)
               .eq(PromptTemplateFavorite::getTemplateId, templateId);
        
        if (favoriteRepository.selectCount(wrapper) > 0) {
            logger.info("用户已收藏该模板: userId={}, templateId={}", userId, templateId);
            return true; // 瀹稿弶鏁归挊蹇ョ礉鏉╂柨娲栭幋鎰
        }
        
        // 濞ｈ濮為弨鎯版
        PromptTemplateFavorite favorite = new PromptTemplateFavorite();
        favorite.setUserId(userId);
        favorite.setTemplateId(templateId);
        favorite.setCreatedTime(LocalDateTime.now());
        
        int result = favoriteRepository.insert(favorite);
        logger.info("用户收藏模板: userId={}, templateId={}, result={}", userId, templateId, result);
        
        return result > 0;
    }

    /**
     * 閸欐牗绉烽弨鎯版濡剝婢?
     */
    public boolean unfavoriteTemplate(Long userId, Long templateId) {
        LambdaQueryWrapper<PromptTemplateFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplateFavorite::getUserId, userId)
               .eq(PromptTemplateFavorite::getTemplateId, templateId);
        
        int result = favoriteRepository.delete(wrapper);
        logger.info("用户取消收藏模板: userId={}, templateId={}, result={}", userId, templateId, result);
        
        return result > 0;
    }

    
    /**
     * 閺嶅洩顔囧Ο鈩冩緲閸掓銆冮惃鍕暪閽樺繒濮搁幀?
     */
    private void markFavoriteStatus(List<PromptTemplate> templates, Long userId) {
        if (templates.isEmpty() || userId == null) {
            return;
        }

        // 閺屻儴顕楅悽銊﹀煕閺€鎯版閻ㄥ嫭澧嶉張澶嬆侀弶绺凞
        LambdaQueryWrapper<PromptTemplateFavorite> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplateFavorite::getUserId, userId);

        List<Long> favoritedTemplateIds = favoriteRepository.selectList(wrapper).stream()
            .map(PromptTemplateFavorite::getTemplateId)
            .collect(Collectors.toList());

        // 閺嶅洩顔囧В蹇庨嚋濡剝婢橀惃鍕暪閽樺繒濮搁幀?
        templates.forEach(template -> {
            template.setIsFavorited(favoritedTemplateIds.contains(template.getId()));
        });
    }

}

