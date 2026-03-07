# 快速修复数据库问题

## 问题
`users` 表缺少 `role` 和 `status` 字段

## 解决方案

### 方法1：使用Navicat或其他数据库工具（推荐）

1. 打开Navicat连接到 `ai_novel` 数据库
2. 找到 `users` 表，右键选择"设计表"
3. 添加以下字段：
   - `role` VARCHAR(32) DEFAULT 'USER' COMMENT '用户角色'
   - `status` VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '用户状态'
4. 保存

### 方法2：使用MySQL命令行

```bash
# 找到MySQL安装目录，通常在：
# C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe

# 或者在Navicat中打开查询窗口，执行以下SQL：
```

```sql
USE ai_novel;

-- 添加role字段
ALTER TABLE users ADD COLUMN role VARCHAR(32) DEFAULT 'USER' COMMENT '用户角色: USER/ADMIN';

-- 添加status字段  
ALTER TABLE users ADD COLUMN status VARCHAR(32) DEFAULT 'ACTIVE' COMMENT '用户状态: ACTIVE/INACTIVE';

-- 创建管理员账号
INSERT INTO users (username, email, password, role, status, created_at, updated_at)
VALUES ('admin', 'admin@novel.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'ADMIN', 'ACTIVE', NOW(), NOW());
```

### 方法3：执行完整的初始化脚本

在Navicat查询窗口中执行 `admin/database/admin_init.sql` 文件

## 验证

执行以下SQL验证字段是否添加成功：

```sql
DESC users;
```

应该能看到 `role` 和 `status` 字段。

## 默认管理员账号

- 用户名: `admin`
- 密码: `admin123`
