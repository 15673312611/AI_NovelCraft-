package com.novel.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.admin.dto.UserDTO;
import com.novel.admin.entity.User;
import com.novel.admin.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public Page<UserDTO> getUsers(String keyword, int page, int size) {
        Page<UserDTO> dtoPage = new Page<>(page, size);
        return userMapper.selectUserPage(dtoPage, keyword);
    }

    public UserDTO getUserById(Long id) {
        return userMapper.selectUserDTOById(id);
    }

    @Transactional
    public void createUser(UserDTO userDTO) {
        User user = new User();
        user.setUsername(userDTO.getUsername());
        user.setEmail(userDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setNickname(userDTO.getNickname());
        user.setStatus(userDTO.getStatus() != null ? userDTO.getStatus() : "ACTIVE");
        userMapper.insert(user);
        
        // Add roles if provided
        if (StringUtils.hasText(userDTO.getRoles())) {
            String[] roleNames = userDTO.getRoles().split(",");
            for (String roleName : roleNames) {
                userMapper.insertUserRole(user.getId(), roleName.trim());
            }
        }
    }

    @Transactional
    public void updateUser(Long id, UserDTO userDTO) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setEmail(userDTO.getEmail());
            user.setNickname(userDTO.getNickname());
            user.setStatus(userDTO.getStatus());
            userMapper.updateById(user);
            
            // Update roles
            if (StringUtils.hasText(userDTO.getRoles())) {
                userMapper.deleteUserRoles(id);
                String[] roleNames = userDTO.getRoles().split(",");
                for (String roleName : roleNames) {
                    userMapper.insertUserRole(id, roleName.trim());
                }
            }
        }
    }

    @Transactional
    public void deleteUser(Long id) {
        userMapper.deleteUserRoles(id);
        userMapper.deleteById(id);
    }

    public Map<String, Object> getUserStats(Long id) {
        Map<String, Object> stats = new HashMap<>();
        // TODO: Add actual statistics queries
        stats.put("novelCount", 0);
        stats.put("aiTaskCount", 0);
        stats.put("totalWordCount", 0);
        return stats;
    }
}
