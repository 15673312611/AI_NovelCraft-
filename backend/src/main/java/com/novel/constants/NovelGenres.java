package com.novel.constants;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 小说分类常量
 * 确保前后端使用一致的分类定义
 */
public class NovelGenres {
    
    // 分类常量
    public static final String XUANHUAN = "xuanhuan";
    public static final String QIHUAN = "qihuan";
    public static final String WUXIA = "wuxia";
    public static final String XIANXIA = "xianxia";
    public static final String KEHUAN = "kehuan";
    public static final String YANQING = "yanqing";
    public static final String XUANYI = "xuanyi";
    public static final String JINGXUAN = "jingxuan";
    public static final String LISHI = "lishi";
    public static final String DUSHI = "dushi";
    public static final String JUNSHI = "junshi";
    public static final String YOUXI = "youxi";
    public static final String TIYU = "tiyu";
    public static final String QITA = "qita";
    
    // 分类标签映射
    private static final Map<String, String> GENRE_LABELS;
    
    static {
        Map<String, String> map = new java.util.HashMap<>();
        map.put(XUANHUAN, "玄幻");
        map.put(QIHUAN, "奇幻");
        map.put(WUXIA, "武侠");
        map.put(XIANXIA, "仙侠");
        map.put(KEHUAN, "科幻");
        map.put(YANQING, "言情");
        map.put(XUANYI, "悬疑");
        map.put(JINGXUAN, "惊悚");
        map.put(LISHI, "历史");
        map.put(DUSHI, "都市");
        map.put(JUNSHI, "军事");
        map.put(YOUXI, "游戏");
        map.put(TIYU, "体育");
        map.put(QITA, "其他");
        GENRE_LABELS = java.util.Collections.unmodifiableMap(map);
    }
    
    // 获取所有分类
    public static List<String> getAllGenres() {
        return Arrays.asList(
            XUANHUAN, QIHUAN, WUXIA, XIANXIA, KEHUAN, YANQING, XUANYI, JINGXUAN,
            LISHI, DUSHI, JUNSHI, YOUXI, TIYU, QITA
        );
    }
    
    // 获取分类标签
    public static String getGenreLabel(String genre) {
        return GENRE_LABELS.getOrDefault(genre, "未知");
    }
    
    // 获取所有分类和标签的映射
    public static Map<String, String> getGenreLabels() {
        return GENRE_LABELS;
    }
    
    // 验证分类是否有效
    public static boolean isValidGenre(String genre) {
        return GENRE_LABELS.containsKey(genre);
    }
    
    // 获取默认分类
    public static String getDefaultGenre() {
        return XUANHUAN;
    }
} 