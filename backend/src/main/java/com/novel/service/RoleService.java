package com.novel.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novel.domain.entity.Role;
import com.novel.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleService {

    @Autowired
    private RoleRepository roleRepository;

    /**
     * 创建角色
     */
    public Role createRole(Role role) {
        // 设置优先级
        if (role.getPriority() == null) {
            Integer maxPriority = roleRepository.findMaxPriority();
            role.setPriority(maxPriority != null ? maxPriority + 1 : 1);
        }
        roleRepository.insert(role);
        return role;
    }

    /**
     * 获取角色
     */
    public Role getRole(Long id) {
        return roleRepository.selectById(id);
    }

    /**
     * 根据角色名称获取角色
     */
    public Role getRoleByName(String name) {
        return roleRepository.findByName(name);
    }

    /**
     * 更新角色
     */
    public Role updateRole(Long id, Role roleData) {
        Role role = roleRepository.selectById(id);
        if (role != null) {
            if (roleData.getName() != null) {
                role.setName(roleData.getName());
            }
            if (roleData.getDisplayName() != null) {
                role.setDisplayName(roleData.getDisplayName());
            }
            if (roleData.getDescription() != null) {
                role.setDescription(roleData.getDescription());
            }
            if (roleData.getActive() != null) {
                role.setActive(roleData.getActive());
            }
            if (roleData.getPriority() != null) {
                role.setPriority(roleData.getPriority());
            }
            
            roleRepository.updateById(role);
            return role;
        }
        return null;
    }

    /**
     * 删除角色
     */
    public boolean deleteRole(Long id) {
        return roleRepository.deleteById(id) > 0;
    }

    /**
     * 获取角色列表（分页）
     */
    public IPage<Role> getRoles(int page, int size, Boolean active) {
        Page<Role> pageParam = new Page<>(page + 1, size);
        if (active != null) {
            return roleRepository.findByActive(active, pageParam);
        } else {
            return roleRepository.selectPage(pageParam, null);
        }
    }

    /**
     * 获取所有激活的角色
     */
    public List<Role> getActiveRoles() {
        return roleRepository.findByActiveTrue();
    }

    /**
     * 搜索角色
     */
    public List<Role> searchRoles(String query) {
        return roleRepository.searchByName(query);
    }

    /**
     * 统计激活的角色数量
     */
    public long countActiveRoles() {
        return roleRepository.countByActiveTrue();
    }

    /**
     * 激活角色
     */
    public Role activateRole(Long id) {
        Role role = roleRepository.selectById(id);
        if (role != null) {
            role.setActive(true);
            roleRepository.updateById(role);
            return role;
        }
        return null;
    }

    /**
     * 禁用角色
     */
    public Role deactivateRole(Long id) {
        Role role = roleRepository.selectById(id);
        if (role != null) {
            role.setActive(false);
            roleRepository.updateById(role);
            return role;
        }
        return null;
    }

    /**
     * 调整角色优先级
     */
    public Role updateRolePriority(Long id, Integer priority) {
        Role role = roleRepository.selectById(id);
        if (role != null) {
            role.setPriority(priority);
            roleRepository.updateById(role);
            return role;
        }
        return null;
    }
}
