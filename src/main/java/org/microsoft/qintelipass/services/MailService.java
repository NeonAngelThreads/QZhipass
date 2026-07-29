package org.microsoft.qintelipass.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.microsoft.qintelipass.exceptions.EmailBindingDeliveryException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Service
public class MailService {
    private static final String SUBJECT = "企智通邮箱绑定验证码";

    private final JavaMailSender mailSender;
    private final String host;
    private final String username;
    private final String password;
    private final String from;
    private final String fromName;

    public MailService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.mail.from:}") String from,
            @Value("${app.mail.from-name:企智通}") String fromName
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.host = host;
        this.username = username;
        this.password = password;
        this.from = from;
        this.fromName = fromName;
    }

    public void sendEmailBindingCode(String recipient, String code) {
        if (mailSender == null
                || !StringUtils.hasText(host)
                || !StringUtils.hasText(username)
                || !StringUtils.hasText(password)
                || !StringUtils.hasText(from)) {
            throw new EmailBindingDeliveryException();
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject(SUBJECT);
            helper.setText("""
                    您正在进行企智通邮箱绑定操作。
                    本次验证码为：%s
                    验证码5分钟内有效，请勿将验证码告知他人。
                    如非本人操作，请忽略本邮件。
                    """.formatted(code));
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            throw new EmailBindingDeliveryException(exception);
        }
    }
}
