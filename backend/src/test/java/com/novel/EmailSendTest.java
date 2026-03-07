package com.novel;

import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * 163邮箱发送测试
 * 直接运行main方法测试邮件发送
 */
public class EmailSendTest {

    public static void main(String[] args) {
        // ========== 配置区域 - 请修改为你的实际配置 ==========
        String smtpHost = "smtp.163.com";
        int smtpPort = 465;  // 465(SSL) 或 25(STARTTLS)
        boolean useSsl = true;  // 465端口用true，25端口用false
        
        String username = "xiaoshuonexs@163.com";  // 你的163邮箱
        String password = "NSUNdjjJPZDMfJkF";  // 163邮箱授权码(不是登录密码!)
        
        String toEmail = "1054036255@qq.com";  // 接收邮件的地址
        // ====================================================

        System.out.println("========== 163邮箱发送测试 ==========");
        System.out.println("SMTP服务器: " + smtpHost);
        System.out.println("SMTP端口: " + smtpPort);
        System.out.println("SSL模式: " + (useSsl ? "是" : "否(STARTTLS)"));
        System.out.println("发件人: " + username);
        System.out.println("收件人: " + toEmail);
        System.out.println("=====================================\n");

        // 测试1: 使用配置的端口
        System.out.println("【测试1】使用配置端口 " + smtpPort + " 发送...");
        boolean success = testSend(smtpHost, smtpPort, useSsl, username, password, toEmail);
        
        if (success) {
            System.out.println("\n✓ 邮件发送成功！");
            return;
        }

        // 测试2: 如果465失败，尝试25端口
        if (smtpPort == 465) {
            System.out.println("\n【测试2】尝试使用25端口(STARTTLS)发送...");
            success = testSend(smtpHost, 25, false, username, password, toEmail);
            
            if (success) {
                System.out.println("\n✓ 邮件发送成功(使用25端口)！");
                System.out.println("建议: 在后台管理系统中将端口改为25，关闭SSL");
                return;
            }
        }

        System.out.println("\n✗ 所有尝试都失败了");
        System.out.println("\n可能的原因:");
        System.out.println("1. 授权码错误 - 必须使用163邮箱的授权码，不是登录密码");
        System.out.println("2. 防火墙拦截 - 检查Windows Defender或杀毒软件");
        System.out.println("3. ISP封锁 - 部分运营商封锁SMTP端口");
        System.out.println("4. 网络问题 - 检查是否可以访问外网");
        System.out.println("\n详细排查步骤请查看: backend/EMAIL_TROUBLESHOOTING.md");
    }

    /**
     * 测试发送邮件
     */
    private static boolean testSend(String host, int port, boolean useSsl, 
                                     String username, String password, String toEmail) {
        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(host);
            mailSender.setPort(port);
            mailSender.setUsername(username);
            mailSender.setPassword(password);
            mailSender.setDefaultEncoding("UTF-8");

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            
            // 超时配置
            props.put("mail.smtp.timeout", "30000");
            props.put("mail.smtp.connectiontimeout", "30000");
            props.put("mail.smtp.writetimeout", "30000");
            
            if (useSsl) {
                // SSL模式
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "false");
                props.put("mail.smtp.ssl.trust", "*");
                props.put("mail.smtp.socketFactory.port", String.valueOf(port));
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
            } else {
                // STARTTLS模式
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "false");
                props.put("mail.smtp.ssl.trust", host);
            }
            
            // 开启调试模式
            props.put("mail.debug", "true");

            System.out.println("正在连接SMTP服务器...");
            
            // 创建邮件
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(username, "AI小说创作系统");
            helper.setTo(toEmail);
            helper.setSubject("【测试邮件】163邮箱发送测试");
            
            String content = buildTestEmailContent();
            helper.setText(content, true);

            System.out.println("正在发送邮件...");
            long startTime = System.currentTimeMillis();
            
            mailSender.send(message);
            
            long endTime = System.currentTimeMillis();
            System.out.println("发送耗时: " + (endTime - startTime) + "ms");
            
            return true;
            
        } catch (Exception e) {
            System.err.println("发送失败: " + e.getMessage());
            
            // 打印详细错误信息
            if (e.getCause() != null) {
                System.err.println("原因: " + e.getCause().getMessage());
            }
            
            // 常见错误提示
            String errorMsg = e.getMessage().toLowerCase();
            if (errorMsg.contains("timeout")) {
                System.err.println("→ 连接超时，可能是防火墙拦截或端口被封锁");
            } else if (errorMsg.contains("authentication failed")) {
                System.err.println("→ 认证失败，请检查邮箱账号和授权码是否正确");
            } else if (errorMsg.contains("connection refused")) {
                System.err.println("→ 连接被拒绝，请检查SMTP服务器地址和端口");
            }
            
            return false;
        }
    }

    /**
     * 构建测试邮件内容
     */
    private static String buildTestEmailContent() {
        return "<!DOCTYPE html>" +
            "<html><head><meta charset='UTF-8'></head><body style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>" +
            "<div style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; border-radius: 10px 10px 0 0;'>" +
            "<h1 style='color: white; margin: 0; text-align: center;'>163邮箱测试</h1>" +
            "</div>" +
            "<div style='background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px;'>" +
            "<p style='font-size: 16px; color: #333;'>这是一封测试邮件。</p>" +
            "<p style='font-size: 16px; color: #333;'>如果您收到这封邮件，说明163邮箱SMTP配置正确！</p>" +
            "<div style='background: #fff; border: 2px solid #667eea; padding: 20px; text-align: center; margin: 20px 0; border-radius: 8px;'>" +
            "<span style='font-size: 24px; font-weight: bold; color: #667eea;'>✓ 测试成功</span>" +
            "</div>" +
            "<p style='font-size: 14px; color: #666;'>发送时间: " + new java.util.Date() + "</p>" +
            "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'>" +
            "<p style='font-size: 12px; color: #999; text-align: center;'>AI小说创作系统 - 邮件发送测试</p>" +
            "</div></body></html>";
    }
}
