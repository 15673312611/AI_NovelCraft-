# API 测试说明

## 问题排查

### 1. 检查后端是否启动
```bash
# 查看8081端口是否被占用
netstat -ano | findstr 8081
```

### 2. 测试基础接口
```bash
# 测试小说列表（不需要认证）
curl http://localhost:8081/admin/novels

# 如果需要认证，先登录获取token
curl -X POST http://localhost:8081/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 3. 测试详情接口
```bash
# 使用获取的token测试
curl http://localhost:8081/admin/novels/176/outline \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"

curl http://localhost:8081/admin/novels/176/volumes \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"

curl http://localhost:8081/admin/novels/176/chapter-outlines \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"

curl http://localhost:8081/admin/novels/176/chapters \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

## 路径映射说明

### 后端配置
- **context-path**: `/admin`
- **Controller**: `@RequestMapping("/novels")`
- **实际路径**: `http://localhost:8081/admin/novels/*`

### 前端配置
- **baseURL**: `/admin`
- **请求路径**: `/novels/*`
- **实际请求**: `/admin/novels/*`

### 完整路径示例
```
前端: request.get('/novels/176/outline')
↓
加上baseURL: /admin/novels/176/outline
↓
代理到后端: http://localhost:8081/admin/novels/176/outline
```

## 常见问题

### 1. 404 错误
**可能原因：**
- 后端没有启动
- 路径不匹配
- Controller 没有被扫描到
- Service 注入失败

**排查步骤：**
1. 检查后端日志，看是否有启动错误
2. 检查 Controller 是否有 `@RestController` 注解
3. 检查 Service 是否有 `@Service` 注解
4. 检查 Mapper 是否有 `@Mapper` 注解
5. 使用 curl 直接测试后端接口

### 2. 401 未授权
**可能原因：**
- 没有登录
- Token 过期
- Token 格式错误

**解决方案：**
1. 先登录获取 token
2. 在请求头中添加 `Authorization: Bearer {token}`

### 3. 500 服务器错误
**可能原因：**
- 数据库连接失败
- SQL 语法错误
- 空指针异常

**排查步骤：**
1. 查看后端日志
2. 检查数据库连接
3. 检查 SQL 语句

## 启动步骤

### 1. 启动后端
```bash
cd admin/backend
mvn spring-boot:run
```

### 2. 启动前端
```bash
cd admin/frontend
npm run dev
```

### 3. 访问测试
打开浏览器访问: `http://localhost:5173/novels/176`

## 调试技巧

### 1. 查看后端日志
后端启动时会打印所有注册的接口：
```
Mapped "{[/novels/{novelId}/outline],methods=[GET]}" onto ...
Mapped "{[/novels/{novelId}/volumes],methods=[GET]}" onto ...
```

### 2. 使用浏览器开发者工具
- 打开 Network 标签
- 查看请求的 URL
- 查看请求的 Headers
- 查看响应的状态码和内容

### 3. 检查前端请求
在 `NovelDetail.tsx` 中添加 console.log：
```typescript
console.log('请求路径:', `/novels/${novelId}/outline`)
console.log('完整URL:', request.defaults.baseURL + `/novels/${novelId}/outline`)
```

## 数据库检查

### 1. 检查表是否存在
```sql
SHOW TABLES LIKE 'novels';
SHOW TABLES LIKE 'novel_outlines';
SHOW TABLES LIKE 'novel_volumes';
SHOW TABLES LIKE 'volume_chapter_outlines';
SHOW TABLES LIKE 'chapters';
```

### 2. 检查数据是否存在
```sql
SELECT * FROM novels WHERE id = 176;
SELECT * FROM novel_outlines WHERE novel_id = 176;
SELECT * FROM novel_volumes WHERE novel_id = 176;
SELECT * FROM volume_chapter_outlines WHERE novel_id = 176 LIMIT 10;
SELECT * FROM chapters WHERE novel_id = 176 LIMIT 10;
```

### 3. 检查字段是否匹配
```sql
DESC novels;
DESC novel_outlines;
DESC novel_volumes;
DESC volume_chapter_outlines;
DESC chapters;
```
