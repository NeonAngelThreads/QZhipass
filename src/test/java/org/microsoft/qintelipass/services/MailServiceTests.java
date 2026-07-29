package org.microsoft.qintelipass.services;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.exceptions.EmailBindingDeliveryException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MailServiceTests {
    @SuppressWarnings("unchecked")
    @Test
    void refusesToFakeSuccessWhenSmtpConfigurationIsMissing() {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        MailService service = new MailService(provider, "", "", "", "", "企智通");

        EmailBindingDeliveryException exception = assertThrows(
                EmailBindingDeliveryException.class,
                () -> service.sendEmailBindingCode("employee@company.com", "123456")
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.CODE_DELIVERY_FAILED);
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendsChinesePlainTextMailThroughTheSingleMailEntry() throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(provider.getIfAvailable()).thenReturn(sender);
        when(sender.createMimeMessage()).thenReturn(message);
        MailService service = new MailService(
                provider,
                "smtp.company.com",
                "sender@company.com",
                "secret",
                "sender@company.com",
                "企智通"
        );

        service.sendEmailBindingCode("employee@company.com", "123456");

        verify(sender).send(message);
        assertThat(message.getSubject()).isEqualTo("企智通邮箱绑定验证码");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("employee@company.com");
        assertThat(message.getContent().toString())
                .contains("您正在进行企智通邮箱绑定操作。")
                .contains("123456")
                .contains("5分钟内有效")
                .contains("如非本人操作，请忽略本邮件。");
    }
}
