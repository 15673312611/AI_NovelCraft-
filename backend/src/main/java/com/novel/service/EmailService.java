package com.novel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * 邮件发送服务
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private SystemAIConfigService configService;

    /**
     * 发送验证码邮件
     */
    public void sendVerificationCode(String toEmail, String code) {
        String fromName = configService.getConfig("email_from_name");
        if (fromName == null || fromName.isEmpty()) {
            fromName = "AI小说创作系统";
        }

        String subject = "【" + fromName + "】登录验证码";
        String content = buildVerificationEmailContent(code, fromName);

        sendEmail(toEmail, subject, content, true);
    }

    /**
     * 发送邮件
     */
    public void sendEmail(String to, String subject, String content, boolean isHtml) {
        Exception lastException = null;

        // 尝试使用配置的端口发送（例如 163: 465/587）
        try {
            JavaMailSender mailSender = createMailSender();
            sendEmailInternal(mailSender, to, subject, content, isHtml);
            logger.info("邮件发送成功: to={}, subject={}", to, subject);
            return;
        } catch (Exception e) {
            lastException = e;
            // 不再尝试25端口：云环境常见封禁/限流，且会引入额外等待
            logger.warn("使用配置端口发送失败: {}: {}", e.getClass().getSimpleName(), safeMessage(e), e);
        }

        // 所有尝试都失败：保留原始异常作为 cause，便于定位线上 535/超时等问题
        String details = rootCauseSummary(lastException);
        logger.error("邮件发送失败: to={}, subject={}, detail={}", to, subject, details, lastException);
        throw new RuntimeException("邮件发送失败: " + details, lastException);
    }

    private static String safeMessage(Throwable t) {
        return t == null ? "" : (t.getMessage() == null ? "" : t.getMessage());
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String rootCauseSummary(Throwable t) {
        Throwable rc = rootCause(t);
        if (rc == null) return "unknown";
        String msg = safeMessage(rc);
        if (msg.isEmpty()) return rc.getClass().getName();
        return rc.getClass().getName() + ": " + msg;
    }
    
    /**
     * 实际发送邮件的内部方法
     */
    private void sendEmailInternal(JavaMailSender mailSender, String to, String subject, String content, boolean isHtml) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        String fromEmail = configService.getConfig("email_smtp_username");
        String fromName = configService.getConfig("email_from_name");
        if (fromName == null || fromName.isEmpty()) {
            fromName = "AI小说创作系统";
        }

        helper.setFrom(fromEmail, fromName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, isHtml);

        mailSender.send(message);
    }

    /**
     * 创建邮件发送器
     */
    private JavaMailSender createMailSender() {
        String portStr = configService.getConfig("email_smtp_port");
        String sslEnabled = configService.getConfig("email_smtp_ssl");
        int port = Integer.parseInt(portStr != null ? portStr : "465");
        boolean useSsl = "true".equalsIgnoreCase(sslEnabled) || port == 465 || port == 994;
        
        return createMailSenderWithPort(port, useSsl);
    }
    
    /**
     * 使用指定端口创建邮件发送器
     */
    private JavaMailSender createMailSenderWithPort(int port, boolean useSsl) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        String host = trimToNull(configService.getConfig("email_smtp_host"));
        String username = trimToNull(configService.getConfig("email_smtp_username"));
        String password = trimToNull(configService.getConfig("email_smtp_password"));

        if (host == null) {
            throw new RuntimeException("邮箱SMTP服务器未配置");
        }
        if (username == null) {
            throw new RuntimeException("邮箱账号未配置");
        }
        if (password == null) {
            throw new RuntimeException("邮箱授权码未配置");
        }

        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        mailSender.setDefaultEncoding("UTF-8");

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        
        // 超时配置 - 增加到30秒
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.connectiontimeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");
        
        // 163邮箱特殊配置
        if (useSsl) {
            // SSL模式 (端口465)
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "false");
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.socketFactory.port", String.valueOf(port));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
            logger.info("邮件配置: host={}, port={}, SSL模式", host, port);
        } else {
            // STARTTLS模式 (端口25)
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "false");
            props.put("mail.smtp.ssl.trust", host);
            logger.info("邮件配置: host={}, port={}, STARTTLS模式", host, port);
        }
        
        // 调试模式（生产环境可关闭）
        props.put("mail.debug", "false");

        return mailSender;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * 构建验证码邮件内容
     */
    private String buildVerificationEmailContent(String code, String appName) {
        String expireMinutes = configService.getConfig("email_code_expire_minutes");
        if (expireMinutes == null || expireMinutes.isEmpty()) {
            expireMinutes = "5";
        }

        return "<!DOCTYPE html>" +
            "<html><head><meta charset='UTF-8'></head><body style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>" +
            "<div style='background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 30px; border-radius: 10px 10px 0 0;'>" +
            "<h1 style='color: white; margin: 0; text-align: center;'>" + appName + "</h1>" +
            "</div>" +
            "<div style='background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px;'>" +
            "<p style='font-size: 16px; color: #333;'>您好！</p>" +
            "<p style='font-size: 16px; color: #333;'>您正在进行登录验证，验证码为：</p>" +
            "<div style='background: #fff; border: 2px dashed #667eea; padding: 20px; text-align: center; margin: 20px 0; border-radius: 8px;'>" +
            "<span style='font-size: 32px; font-weight: bold; color: #667eea; letter-spacing: 8px;'>" + code + "</span>" +
            "</div>" +
            "<p style='font-size: 14px; color: #666;'>验证码有效期为 <strong>" + expireMinutes + " 分钟</strong>，请尽快使用。</p>" +
            "<p style='font-size: 14px; color: #999;'>如果这不是您本人的操作，请忽略此邮件。</p>" +
            "<hr style='border: none; border-top: 1px solid #eee; margin: 20px 0;'>" +
            "<p style='font-size: 12px; color: #999; text-align: center;'>此邮件由系统自动发送，请勿回复</p>" +
            "</div></body></html>";
    }

    /**
     * 测试邮箱配置
     */
    public boolean testConnection() {
        try {
            JavaMailSenderImpl mailSender = (JavaMailSenderImpl) createMailSender();
            mailSender.testConnection();
            return true;
        } catch (Exception e) {
            logger.error("邮箱连接测试失败", e);
            return false;
        }
    }
}
