package com.novel.agentic.util;

import java.util.*;

/**
 * JDK 8 兼容的集合工具类
 * 
 * 替代JDK 9+的Map.of(), List.of(), Set.of()
 */
public class CollectionUtils {
    
    /**
     * 创建不可变Map（JDK 8兼容版的Map.of）
     */
    public static <K, V> Map<K, V> mapOf(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("键值对数量必须是偶数");
        }
        
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) keyValues[i];
            @SuppressWarnings("unchecked")
            V value = (V) keyValues[i + 1];
            map.put(key, value);
        }
        
        return Collections.unmodifiableMap(map);
    }
    
    /**
     * 创建可变Map
     */
    public static <K, V> Map<K, V> hashMapOf(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("键值对数量必须是偶数");
        }
        
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            @SuppressWarnings("unchecked")
            K key = (K) keyValues[i];
            @SuppressWarnings("unchecked")
            V value = (V) keyValues[i + 1];
            map.put(key, value);
        }
        
        return map;
    }
    
    /**
     * 创建不可变List（JDK 8兼容版的List.of）
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... elements) {
        return Collections.unmodifiableList(Arrays.asList(elements));
    }
    
    /**
     * 创建不可变Set（JDK 8兼容版的Set.of）
     */
    @SafeVarargs
    public static <T> Set<T> setOf(T... elements) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(elements)));
    }
}


