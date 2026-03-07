# 快速诊断 - 404 错误

## 立即检查清单

### ✅ 1. 后端是否正常启动？
查看后端控制台，应该看到：
```
Started AdminApplication in X.XXX seconds
```

### ✅ 2. 接口是否注册成功？
后端启动日志中搜索：
```
Mapped "{[/novels/{novelId}/outline]
```

如果没有看到，说明 Controller 没有被扫描到。

### ✅ 3. 前端请求路径是否正确？
打开浏览器 F12 -> Network，查看实际请求的 URL：
- **正确**: `http://localhost:8081/admin/novels/176/outline`
- **错误**: `http://localhost:8081/novels/176/outline` (缺少 /admin)
- **错误**: `http://localhost:8081/admin/api/admin/novels/176/outline` (重复)

### ✅ 4. 是否使用了正确的 request 实例？
检查 `NovelDetail.tsx` 的导入：
- **正确**: `import request from '../../services/request'`
- **错误**: `import axios from 'axios'`

## 最可能的原因

### 原因 1: 后端没有重启
**症状**: 修改代码后，接口仍然 404

**解决**: 
```bash
# 停止后端
Ctrl + C

# 重新启动
cd admin/backend
mvn spring-boot:run
```

### 原因 2: Service 注入失败
**症状**: 后端启动报错，提示找不到 Bean

**检查**: 
1. `NovelDetailService` 是否有 `@Service` 注解
2. `NovelDetailMapper` 是否有 `@Mapper` 注解
3. `AdminNovelController` 的构造函数是否包含 `novelDetailService`

**解决**: 确保所有类都有正确的注解

### 原因 3: 数据库表不存在
**症状**: 接口返回 500 错误，而不是 404

**检查**:
```sql
SHOW TABLES LIKE 'novel_outlines';
SHOW TABLES LIKE 'novel_volumes';
SHOW TABLES LIKE 'volume_chapter_outlines';
```

**解决**: 执行数据库迁移脚本

### 原因 4: 前端代理配置问题
**症状**: 前端请求被拦截或路径错误

**检查**: `vite.config.ts` 的 proxy 配置

**解决**: 确保代理配置正确

## 立即执行的修复步骤

### 步骤 1: 确认后端接口
```bash
# 在后端目录执行
cd admin/backend

# 查看 Controller 文件
cat src/main/java/com/novel/admin/controller/AdminNovelController.java | grep "@GetMapping"
```

应该看到：
```java
@GetMapping("/{novelId}/outline")
@GetMapping("/{novelId}/volumes")
@GetMapping("/{novelId}/chapter-outlines")
@GetMapping("/{novelId}/chapters")
```

### 步骤 2: 确认前端请求
```bash
# 在前端目录执行
cd admin/frontend

# 查看请求代码
cat src/pages/Novels/NovelDetail.tsx | grep "request.get"
```

应该看到：
```typescript
request.get(`/novels/${novelId}/outline`)
request.get(`/novels/${novelId}/volumes`)
```

### 步骤 3: 重启服务
```bash
# 1. 停止后端 (Ctrl+C)
# 2. 停止前端 (Ctrl+C)

# 3. 重新启动后端
cd admin/backend
mvn spring-boot:run

# 4. 等待后端完全启动后，重新启动前端
cd admin/frontend
npm run dev
```

### 步骤 4: 测试接口
打开浏览器访问: `http://localhost:5173/novels/176`

打开 F12 -> Network，刷新页面，查看请求：
- 如果看到 404: 后端接口没有注册
- 如果看到 401: 需要登录
- 如果看到 500: 后端代码有错误
- 如果看到 200: 成功！

## 如果还是 404

### 检查点 1: 后端日志
查看后端启动日志，搜索 "NovelDetailService"：
```
Creating bean 'novelDetailService'
```

如果没有，说明 Service 没有被创建。

### 检查点 2: Controller 注入
查看后端启动日志，搜索 "AdminNovelController"：
```
Creating bean 'adminNovelController'
```

如果报错，说明依赖注入失败。

### 检查点 3: Mapper 扫描
查看后端启动日志，搜索 "NovelDetailMapper"：
```
Creating MapperFactoryBean with name 'novelDetailMapper'
```

如果没有，说明 Mapper 没有被扫描到。

### 检查点 4: 数据库连接
查看后端启动日志，搜索 "datasource"：
```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
```

如果报错，说明数据库连接失败。

## 终极解决方案

如果以上都检查了还是不行，执行以下步骤：

### 1. 清理并重新编译
```bash
# 后端
cd admin/backend
mvn clean install -DskipTests

# 前端
cd admin/frontend
rm -rf node_modules dist
npm install
npm run build
```

### 2. 检查代码是否被正确保存
有时候 IDE 的自动格式化会导致代码没有保存。

### 3. 查看完整的错误日志
把后端的完整启动日志发给我，我帮你分析。

### 4. 使用 curl 直接测试
```bash
curl -v http://localhost:8081/admin/novels/176/outline
```

查看返回的状态码和响应内容。
