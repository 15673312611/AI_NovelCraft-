# 安全漏洞审计报告

## 🚨 严重漏洞

### 1. 越权访问漏洞 (IDOR - Insecure Direct Object Reference)

**影响范围**: 几乎所有Controller

**漏洞描述**: 
- 用户可以通过修改ID参数访问、修改、删除其他用户的数据
- 没有验证资源所有权
- 攻击者可以遍历ID删除所有数据

**受影响的接口**:

#### NovelController.java
- ❌ `DELETE /novels/{id}` - 可以删除任何用户的小说
- ❌ `PUT /novels/{id}` - 可以修改任何用户的小说
- ❌ `GET /novels/{id}` - 可以查看任何用户的小说（如果是私密的）
- ❌ `PUT /novels/{novelId}/outline` - 可以修改任何小说的大纲

#### ChapterController.java
- ❌ `DELETE /chapters/{id}` - 可以删除任何章节
- ❌ `PUT /chapters/{id}` - 可以修改任何章节
- ❌ `POST /chapters/{id}/publish` - 可以发布任何章节

#### NovelDocumentController.java
- ❌ `DELETE /documents/{id}` - 可以删除任何文档
- ❌ `PUT /documents/{id}` - 可以修改任何文档

#### ReferenceFileController.java
- ❌ `DELETE /novels/{novelId}/references/{id}` - 可以删除任何参考文件

#### NovelFolderController.java
- ❌ `DELETE /novels/{novelId}/folders/{folderId}` - 可以删除任何文件夹

#### VolumeController.java
- ❌ `DELETE /volumes/{volumeId}` - 可以删除任何卷

#### PromptTemplateController.java
- ❌ `DELETE /prompt-templates/{id}` - 可以删除任何用户的模板

#### AITaskController.java
- ❌ `DELETE /ai-tasks/{id}` - 可以删除任何AI任务

#### AIConversationController.java
- ❌ `DELETE /novels/{novelId}/ai-history/{id}` - 可以删除任何对话
- ❌ `DELETE /novels/{novelId}/ai-history` - 可以清空任何小说的对话

### 2. 批量数据泄露

**NovelController.java**
- ✅ 已修复: `GET /novels` - 现在只返回当前用户的小说

### 3. 未授权访问

**QimaoScraperController.java**
- ⚠️ `POST /qimao/scrape` - 爬虫接口没有权限控制，任何人都可以触发爬虫

## 修复方案

### 方案1: 在Service层添加权限验证（推荐）

```java
public boolean deleteNovel(Long novelId) {
    Long currentUserId = AuthUtils.getCurrentUserId();
    Novel novel = getById(novelId);
    
    if (novel == null) {
        throw new RuntimeException("小说不存在");
    }
    
    if (!novel.getAuthorId().equals(currentUserId)) {
        throw new RuntimeException("无权删除此小说");
    }
    
    return novelRepository.deleteById(novelId) > 0;
}
```

### 方案2: 在Controller层添加权限验证

```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteNovel(@PathVariable Long id) {
    Long currentUserId = AuthUtils.getCurrentUserId();
    Novel novel = novelService.getById(id);
    
    if (novel == null) {
        return ResponseEntity.notFound().build();
    }
    
    if (!novel.getAuthorId().equals(currentUserId)) {
        return ResponseEntity.status(403).build();
    }
    
    boolean deleted = novelService.deleteNovel(id);
    return deleted ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
}
```

### 方案3: 使用Spring Security注解（最佳实践）

```java
@PreAuthorize("@novelSecurityService.canAccessNovel(#id)")
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteNovel(@PathVariable Long id) {
    // ...
}
```

## 优先级修复列表

### P0 - 立即修复（数据安全）
1. ✅ NovelController - 小说增删改查
2. ✅ ChapterController - 章节增删改查
3. ✅ NovelDocumentController - 文档增删改查
4. ✅ VolumeController - 卷增删改查
5. ✅ ReferenceFileController - 参考文件增删改查
6. ✅ NovelFolderController - 文件夹增删改查

### P1 - 高优先级
7. PromptTemplateController - 模板增删改查
8. AITaskController - AI任务管理
9. AIConversationController - 对话历史管理

### P2 - 中优先级
10. QimaoScraperController - 爬虫接口权限控制
11. AiGeneratorController - AI生成器管理

## 测试方法

### 1. 越权删除测试
```bash
# 用户A创建小说，获得ID=1
# 用户B登录后尝试删除
curl -X DELETE http://localhost:8080/novels/1 \
  -H "Authorization: Bearer <用户B的token>"

# 预期: 403 Forbidden
# 实际: 200 OK（漏洞）
```

### 2. 遍历攻击测试
```bash
# 攻击者可以遍历所有ID删除数据
for i in {1..1000}; do
  curl -X DELETE http://localhost:8080/novels/$i \
    -H "Authorization: Bearer <token>"
done
```

## 安全建议

1. **所有资源操作都必须验证所有权**
2. **使用统一的权限验证工具类**
3. **添加操作日志记录**
4. **实施API访问频率限制**
5. **添加敏感操作的二次确认**
6. **定期进行安全审计**
