# 后台管理系统安装指南

## 环境要求

### 前端
- Node.js >= 16.x
- npm >= 8.x

### 后端
- JDK 17
- Maven 3.6+
- MySQL 8.0+

## 安装步骤

### 1. 数据库初始化

```bash
# 连接到MySQL
mysql -u root -p

# 执行初始化脚本
source admin/database/admin_init.sql
```

### 2. 后端安装

```bash
cd admin/backend

# 修改配置文件
# 编辑 src/main/resources/application.yml
# 修改数据库连接信息

# 编译打包
mvn clean package -DskipTests

# 运行
java -jar target/novel-admin-backend-1.0.0.jar

# 或者使用Maven运行
mvn spring-boot:run
```

后端将在 http://localhost:8081/admin 启动

### 3. 前端安装

```bash
cd admin/frontend

# 安装依赖
npm install

# 开发模式运行
npm run dev

# 生产构建
npm run build
```

前端将在 http://localhost:5174 启动

## 默认账号

- 用户名: `admin`
- 密码: `admin123`

**重要：首次登录后请立即修改密码！**

## 配置说明

### 后端配置 (application.yml)

```yaml
server:
  port: 8081  # 后端端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_novel  # 数据库地址
    username: root  # 数据库用户名
    password: root  # 数据库密码

jwt:
  secret: your-secret-key  # JWT密钥，生产环境请修改
  expiration: 86400000  # Token过期时间(毫秒)
```

### 前端配置 (vite.config.ts)

```typescript
server: {
  port: 5174,  // 前端端口
  proxy: {
    '/admin': {
      target: 'http://localhost:8081',  // 后端地址
      changeOrigin: true,
    },
  },
}
```

## 生产部署

### 使用Docker部署

```bash
# 构建前端
cd admin/frontend
npm run build

# 构建后端
cd admin/backend
mvn clean package -DskipTests

# 使用Docker Compose部署
cd admin
docker-compose up -d
```

### 使用Nginx反向代理

```nginx
server {
    listen 80;
    server_name admin.yourdomain.com;

    # 前端静态文件
    location / {
        root /path/to/admin/frontend/dist;
        try_files $uri $uri/ /index.html;
    }

    # 后端API代理
    location /admin {
        proxy_pass http://localhost:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## 常见问题

### 1. 无法连接数据库
- 检查MySQL是否启动
- 检查数据库连接配置是否正确
- 检查防火墙设置

### 2. 前端无法访问后端API
- 检查后端是否正常启动
- 检查Vite代理配置
- 检查CORS配置

### 3. JWT Token验证失败
- 检查JWT密钥配置
- 检查Token是否过期
- 清除浏览器缓存和localStorage

## 技术支持

如有问题，请查看项目文档或提交Issue。
