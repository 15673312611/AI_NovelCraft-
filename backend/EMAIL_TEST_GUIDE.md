# 163邮箱发送测试指南

## 快速测试

### 1. 修改配置
打开文件: `backend/src/test/java/com/novel/EmailSendTest.java`

修改以下配置（第14-20行）:
```java
String smtpHost = "smtp.163.com";
int smtpPort = 465;  // 465(SSL) 或 25(STARTTLS)
boolean useSsl = true;  // 465端口用true，25端口用false

String username = "your_email@163.com";  // 改成你的163邮箱
String password = "your_auth_code";  // 改成你的163邮箱授权码
String toEmail = "test@qq.com";  // 改成接收测试邮件的地址
```

### 2. 获取163邮箱授权码

**重要**: 必须使用授权码，不是登录密码！

步骤:
1. 登录163邮箱网页版: https://mail.163.com
2. 点击右上角"设置" -> "POP3/SMTP/IMAP"
3. 找到"SMTP服务"，点击"开启"
4. 按提示发送短信验证
5. 获得授权码（类似: `ABCD1234EFGH5678`）
6. 将授权码填入上面的 `password` 字段

### 3. 运行测试

#### 方法1: 使用IDE运行
- 在IDEA中打开 `EmailSendTest.java`
- 右键点击 `main` 方法
- 选择 "Run 'EmailSendTest.main()'"

#### 方法2: 使用Maven命令
```bash
cd backend
mvn test-compile
mvn exec:java -Dexec.mainClass="com.novel.EmailSendTest" -Dexec.classpathScope=test
```

#### 方法3: 编译后直接运行
```bash
cd backend
mvn test-compile
java -cp "target/test-classes;target/classes;%USERPROFILE%\.m2\repository\*" com.novel.EmailSendTest
```

### 4. 查看结果

#### 成功的输出示例:
```
========== 163邮箱发送测试 ==========
SMTP服务器: smtp.163.com
SMTP端口: 465
SSL模式: 是
发件人: your_email@163.com
收件人: test@qq.com
=====================================

【测试1】使用配置端口 465 发送...
正在连接SMTP服务器...
正在发送邮件...
发送耗时: 2345ms

✓ 邮件发送成功！
```

#### 失败的输出示例:
```
【测试1】使用配置端口 465 发送...
正在连接SMTP服务器...
正在发送邮件...
发送失败: Mail server connection failed
原因: java.net.SocketTimeoutException: Read timed out
→ 连接超时，可能是防火墙拦截或端口被封锁

【测试2】尝试使用25端口(STARTTLS)发送...
正在连接SMTP服务器...
正在发送邮件...
发送耗时: 1823ms

✓ 邮件发送成功(使用25端口)！
建议: 在后台管理系统中将端口改为25，关闭SSL
```

## 常见问题

### Q1: 提示"认证失败"
**原因**: 授权码错误或未开启SMTP服务

**解决**:
1. 确认使用的是授权码，不是登录密码
2. 重新获取授权码
3. 确认163邮箱SMTP服务已开启

### Q2: 提示"连接超时"
**原因**: 防火墙拦截或ISP封锁端口

**解决**:
1. 临时关闭Windows Defender防火墙测试
2. 关闭杀毒软件测试
3. 尝试使用25端口（将配置中的 `smtpPort` 改为 `25`，`useSsl` 改为 `false`）
4. 检查网络是否可以访问外网

### Q3: 465端口失败，25端口成功
**原因**: ISP封锁了465端口

**解决**:
在后台管理系统中修改配置:
- SMTP端口: 25
- 启用SSL: 关闭

### Q4: 两个端口都失败
**原因**: 可能是网络问题或配置错误

**解决**:
1. 检查网络连接
2. 确认授权码正确
3. 尝试在其他网络环境测试（如手机热点）
4. 查看详细错误日志

## 测试网络连通性

### Windows PowerShell:
```powershell
# 测试465端口
Test-NetConnection -ComputerName smtp.163.com -Port 465

# 测试25端口
Test-NetConnection -ComputerName smtp.163.com -Port 25
```

### Windows CMD (需要启用telnet):
```cmd
telnet smtp.163.com 465
telnet smtp.163.com 25
```

如果连接成功，会显示连接信息。如果超时或拒绝，说明端口不通。

## 调试模式

测试代码已经开启了 `mail.debug=true`，会输出详细的SMTP通信日志，包括:
- 连接过程
- 认证过程
- 发送过程
- 错误详情

查看这些日志可以帮助定位问题。

## 成功后的下一步

如果测试成功:
1. 记住成功的端口和SSL配置
2. 在后台管理系统中填入相同的配置
3. 重启后端服务
4. 在注册页面测试验证码发送

## 其他邮箱服务

如果163邮箱始终无法使用，可以尝试其他邮箱:

### QQ邮箱
- SMTP服务器: smtp.qq.com
- SSL端口: 465 或 587
- 需要开启SMTP服务并获取授权码

### Gmail
- SMTP服务器: smtp.gmail.com
- SSL端口: 465 或 587
- 需要开启"不够安全的应用访问权限"

### 阿里云邮箱
- SMTP服务器: smtp.aliyun.com
- SSL端口: 465
- 非SSL端口: 25
