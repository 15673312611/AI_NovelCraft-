package com.novel.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.User;
import com.novel.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    /**
     * 创建用户
     */
    public User createUser(User user) {
        userRepository.insert(user);
        return user;
    }

    /**
     * 获取用户
     */
    public User getUser(Long id) {
        return userRepository.selectById(id);
    }

    /**
     * 根据用户名获取用户
     */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 根据邮箱获取用户
     */
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * 根据用户名或邮箱获取用户
     */
    public User getUserByUsernameOrEmail(String usernameOrEmail) {
        return userRepository.findByUsernameOrEmail(usernameOrEmail);
    }

    /**
     * 更新用户
     */
    public User updateUser(Long id, User userData) {
        User user = userRepository.selectById(id);
        if (user != null) {
            if (userData.getUsername() != null) {
                user.setUsername(userData.getUsername());
            }
            if (userData.getEmail() != null) {
                user.setEmail(userData.getEmail());
            }
            if (userData.getPassword() != null) {
                user.setPassword(userData.getPassword());
            }
            if (userData.getNickname() != null) {
                user.setNickname(userData.getNickname());
            }
            if (userData.getAvatarUrl() != null) {
                user.setAvatarUrl(userData.getAvatarUrl());
            }
            if (userData.getBio() != null) {
                user.setBio(userData.getBio());
            }
            if (userData.getStatus() != null) {
                user.setStatus(userData.getStatus());
            }
            if (userData.getEmailVerified() != null) {
                user.setEmailVerified(userData.getEmailVerified());
            }
            
            userRepository.updateById(user);
            return user;
        }
        return null;
    }

    /**
     * 删除用户
     */
    public boolean deleteUser(Long id) {
        return userRepository.deleteById(id) > 0;
    }

    /**
     * 获取用户列表（分页）
     */
    public IPage<User> getUsers(int page, int size, String status) {
        Page<User> pageParam = new Page<>(page + 1, size);
        // 简化处理，不再支持按状态过滤
        return userRepository.selectPage(pageParam, null);
    }



    /**
     * 获取用户档案
     */
    public Object getUserProfile(Long userId) {
        User user = userRepository.selectById(userId);
        if (user != null) {
            // 返回用户基本信息，隐藏敏感字段
            java.util.Map<String, Object> profile = new java.util.HashMap<>();
            profile.put("id", user.getId());
            profile.put("username", user.getUsername());
            profile.put("nickname", user.getNickname());
            profile.put("email", user.getEmail());
            profile.put("avatarUrl", user.getAvatarUrl());
            profile.put("bio", user.getBio());
            profile.put("emailVerified", user.getEmailVerified());
            profile.put("status", user.getStatus());
            profile.put("createdAt", user.getCreatedAt());
            return profile;
        }
        return null;
    }

    /**
     * 更新用户设置
     */
    public Object updateUserSettings(Long userId, Object userSettings) {
        User user = userRepository.selectById(userId);
        if (user != null) {
            // 这里简化处理，实际应该根据具体的设置类型进行更新
            // 假设userSettings包含可更新的设置字段
            if (userSettings instanceof java.util.Map) {
                java.util.Map<String, Object> settings = (java.util.Map<String, Object>) userSettings;
                // 更新允许的设置字段
                // 具体实现根据需求调整
            }
            userRepository.updateById(user);
            return getUserProfile(userId);
        }
        return null;
    }

    /**
     * 获取用户写作统计
     */
    public Object getUserWritingStatistics(Long userId, String period) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        // 模拟统计数据，实际应该从数据库查询
        stats.put("totalWords", 0);
        stats.put("totalChapters", 0);
        stats.put("totalNovels", 0);
        stats.put("avgWordsPerDay", 0);
        stats.put("currentStreak", 0);
        stats.put("longestStreak", 0);
        stats.put("writingDays", 0);
        stats.put("period", period != null ? period : "month");
        
        return stats;
    }

    /**
     * 获取最近活动
     */
    public Object getRecentActivities(Long userId, Integer limit) {
        java.util.List<java.util.Map<String, Object>> activities = new java.util.ArrayList<>();
        
        // 模拟最近活动数据，实际应该从数据库查询相关的操作记录
        // 可能包括：创建小说、发布章节、完成任务等
        
        return activities;
    }

    /**
     * 获取用户仪表板数据
     */
    public Object getUserDashboard(Long userId) {
        java.util.Map<String, Object> dashboard = new java.util.HashMap<>();
        
        // 组合各种统计数据
        dashboard.put("profile", getUserProfile(userId));
        dashboard.put("writingStats", getUserWritingStatistics(userId, "week"));
        dashboard.put("recentActivities", getRecentActivities(userId, 10));
        
        // 添加其他仪表板相关数据
        dashboard.put("notifications", new java.util.ArrayList<>());
        dashboard.put("quickActions", new java.util.ArrayList<>());
        
        return dashboard;
    }
}
