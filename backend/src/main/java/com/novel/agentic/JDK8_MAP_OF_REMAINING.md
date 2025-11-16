# 剩余需要修复的 Map.of() / List.of() / Set.of()

## ✅ 已完成修复的文件

1. ✅ **AgentOrchestrator.java** - 4处
2. ✅ **AgenticChapterWriter.java** - 2处  
3. ✅ **EntityExtractionService.java** - 2处
4. ✅ **Neo4jGraphService.java** - 7处 + 添加了辅助方法
5. ✅ **GraphInitializationService.java** - 4处

---

## ⚠️ 还需要手动修复的文件

### Controller层（简单，返回Map）

1. **DiagnosticsController.java** - 3处
   - Line 54: `Map.of("message", "实体抽取服务未启用")`
   - Line 69: `Map.of("message", "实体抽取服务未启用")`
   - Line 74-76: `Map.of("success", true, "message", "重试任务已提交")`
   - Line 80-82: `Map.of("success", false, "message", e.getMessage())`

2. **GraphManagementController.java** - 7处
   - Line 34: `Map.of("error", "Neo4j未启用...")`
   - Line 46: `Map.of("error", "实体抽取服务未启用")`
   - Line 56: `Map.of("status", "success"...)`
   - Line 59: `Map.of("status", "error"...)`
   - Line 69: `Map.of("error", "Neo4j未启用")`
   - Line 74: `Map.of("status", "success"...)`
   - Line 77: `Map.of("status", "error"...)`
   - Line 86-88: `Map.of("neo4jEnabled", ..., "extractionEnabled", ...)`

3. **AgenticWritingController.java** - 1处
   - Line 100: `List.of("ReAct决策循环", ...)`

### Tool层（简单，Map参数）

4. **GetOutlineTool.java**
5. **GetVolumeBlueprintTool.java**
6. **GetRecentChaptersTool.java**
7. **GetRelevantEventsTool.java**
8. **GetUnresolvedForeshadowsTool.java**
9. **GetWorldRulesTool.java**

### Service层

10. **GraphDatabaseService.java** - 2处（内存模拟版）
    - Line 112: `Map.of("name", "主线：成长之路", ...)`
    - Line 140: `Map.of("name", "力量体系", ...)`

---

## 🔧 修复模式

### 单个Map.of()
```java
// ❌ JDK 9+
return Map.of("key", value);

// ✅ JDK 8
Map<String, Object> result = new HashMap<>();
result.put("key", value);
return result;
```

### 多个键值对的Map.of()
```java
// ❌ JDK 9+
return Map.of(
    "key1", value1,
    "key2", value2,
    "key3", value3
);

// ✅ JDK 8
Map<String, Object> result = new HashMap<>();
result.put("key1", value1);
result.put("key2", value2);
result.put("key3", value3);
return result;
```

### List.of()
```java
// ❌ JDK 9+
List<String> list = List.of("item1", "item2", "item3");

// ✅ JDK 8
List<String> list = new ArrayList<>();
list.add("item1");
list.add("item2");
list.add("item3");

// 或使用Arrays.asList（不可变）
List<String> list = Arrays.asList("item1", "item2", "item3");
```

### Set.of()
```java
// ❌ JDK 9+
Set<String> set = Set.of("item1", "item2", "item3");

// ✅ JDK 8
Set<String> set = new HashSet<>();
set.add("item1");
set.add("item2");
set.add("item3");
```

---

## 📊 统计

- **已修复**: 5个文件，约20处
- **待修复**: 约10个文件，约15-20处

---

## 🚀 快速批量修复建议

对于Controller层的简单`Map.of()`返回，可以：

1. 创建工具方法：
```java
// 在基类或工具类中
public static Map<String, Object> mapOf(Object... keyValues) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
        map.put((String) keyValues[i], keyValues[i + 1]);
    }
    return map;
}
```

2. 全局替换：
```java
// 替换所有
Map.of(  →  MapUtils.mapOf(
List.of( →  Arrays.asList(
Set.of(  →  new HashSet<>(Arrays.asList(
```

这样可以减少大量重复代码！


