# 163邮箱发送超时问题排查指南

## 问题现象
```
java.net.SocketTimeoutException: Read timed out
```

## 已实施的修复

### 1. 增加超时时间
- 连接超时: 30秒
- 读取超时: 30秒
- 写入超时: 30秒

### 2. SSL配置优化
- 添加 `mail.smtp.ssl.checkserveridentity=false`
- 添加 `mail.smtp.ssl.trust=*`
- 根据端口自动选择SSL/STARTTLS模式

### 3. 自动端口切换
- 如果465端口(SSL)失败，自动尝试25端口(STARTTLS)
- 提高发送成功率

## 常见原因和解决方案

### 1. 防火墙/杀毒软件拦截
**症状**: 连接超时，无法建立连接

**解决方案**:
- 检查Windows Defender防火墙设置
- 临时关闭杀毒软件测试
- 在防火墙中允许Java程序访问网络
- 允许出站连接到端口465和25

### 2. ISP封锁SMTP端口
**症状**: 465端口超时，但25端口可能正常

**解决方案**:
- 尝试使用25端口(STARTTLS模式)
- 在后台管理系统中修改配置:
  - SMTP端口: 25
  - 启用SSL: 关闭

### 3. 授权码错误
**症状**: 认证失败或连接被拒绝

**解决方案**:
- 登录163邮箱网页版
- 进入"设置" -> "POP3/SMTP/IMAP"
- 开启"SMTP服务"
- 获取"授权码"(不是登录密码!)
- 在后台管理系统中填入授权码

### 4. 网络代理问题
**症状**: 连接超时

**解决方案**:
- 检查系统代理设置
- 如果使用VPN，尝试关闭后测试
- 确保Java程序可以直连外网

## 测试步骤

### 1. 测试网络连通性
```bash
# Windows CMD
telnet smtp.163.com 465
telnet smtp.163.com 25

# 如果telnet不可用，使用PowerShell
Test-NetConnection -ComputerName smtp.163.com -Port 465
Test-NetConnection -ComputerName smtp.163.com -Port 25
```

### 2. 查看后端日志
查找以下关键信息:
- `邮件配置: host=smtp.163.com, port=465, SSL模式`
- `尝试使用25端口(STARTTLS)发送邮件...`
- 具体的错误堆栈信息

### 3. 测试不同端口组合

#### 配置1: 465端口 + SSL
- SMTP服务器: smtp.163.com
- SMTP端口: 465
- 启用SSL: 是

#### 配置2: 25端口 + STARTTLS
- SMTP服务器: smtp.163.com
- SMTP端口: 25
- 启用SSL: 否

#### 配置3: 994端口 + SSL (备用)
- SMTP服务器: smtp.163.com
- SMTP端口: 994
- 启用SSL: 是

## 163邮箱官方配置

### 发送邮件服务器(SMTP)
- 服务器地址: smtp.163.com
- SSL端口: 465 或 994
- 非SSL端口: 25
- 需要身份验证: 是
- 账号: 完整邮箱地址
- 密码: 授权码(不是登录密码)

## 代码改进说明

### 自动重试机制
```java
// 1. 首先尝试配置的端口
// 2. 如果失败且配置的是465，自动尝试25端口
// 3. 记录详细日志便于排查
```

### 更宽松的SSL配置
```java
props.put("mail.smtp.ssl.checkserveridentity", "false");
props.put("mail.smtp.ssl.trust", "*");
```

这样可以避免SSL证书验证问题导致的连接失败。

## 如果问题仍然存在

1. **检查163邮箱设置**
   - 确认SMTP服务已开启
   - 确认使用的是授权码而不是登录密码
   - 检查是否有发送频率限制

2. **检查服务器环境**
   - 确认服务器可以访问外网
   - 检查是否有出站流量限制
   - 尝试在本地开发环境测试

3. **使用其他邮箱服务**
   - QQ邮箱: smtp.qq.com (端口465/587)
   - Gmail: smtp.gmail.com (端口465/587)
   - 阿里云邮箱: smtp.aliyun.com (端口465/25)

4. **启用调试模式**
   修改 `EmailService.java`:
   ```java
   props.put("mail.debug", "true");
   ```
   重新编译后查看详细的SMTP通信日志。
