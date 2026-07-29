package org.microsoft.qintelipass.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.enums.UserRole;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.util.VerificationCodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:email_binding;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(EmailBindingPersistenceIntegrationTests.PasswordEncoderTestConfiguration.class)
class EmailBindingPersistenceIntegrationTests {
    private static final Long USER_ID = 88001L;
    private static final String EMAIL = "employee@company.com";
    private static final String CODE = "804271";
    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void bindingPersistsToExistingUsersEmailColumnAndSurvivesReload() throws Exception {
        User user = User.builder()
                .id(USER_ID)
                .phone("13800138801")
                .name("email-binding-user")
                .email(null)
                .status(UserStatus.NORMAL)
                .role(UserRole.USER)
                .updatedAt(LocalDateTime.now())
                .restored(false)
                .build();
        userRepository.saveAndFlush(user);

        RedisService redisService = mock(RedisService.class);
        EmailValidationService validationService = mock(EmailValidationService.class);
        MailService mailService = mock(MailService.class);
        VerificationCodeGenerator codeGenerator = mock(VerificationCodeGenerator.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        ObjectMapper objectMapper = new ObjectMapper();
        String codeHash = passwordEncoder.encode(CODE);
        String record = objectMapper.writeValueAsString(new VerificationRecordForTest(
                USER_ID,
                EMAIL,
                codeHash,
                NOW.getEpochSecond(),
                NOW.plusSeconds(300).getEpochSecond()
        ));

        when(validationService.normalize(EMAIL)).thenReturn(EMAIL);
        when(redisService.getValue(startsWith("email:binding:code:"))).thenReturn(record);
        when(redisService.deleteIfValueMatches(anyString(), eq(record))).thenReturn(true);

        EmailBindingService service = new EmailBindingService(
                userRepository,
                redisService,
                validationService,
                mailService,
                objectMapper,
                passwordEncoder,
                codeGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.verifyAndBind(USER_ID, EMAIL, CODE).bound()).isTrue();
        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findById(USER_ID).orElseThrow();
        assertThat(reloaded.getEmail()).isEqualTo(EMAIL);
        assertThat(userRepository.existsByEmailIgnoreCaseAndIdNot(EMAIL.toUpperCase(), 99001L))
                .isTrue();
    }

    private record VerificationRecordForTest(
            Long userId,
            String email,
            String codeHash,
            long createdAtEpochSecond,
            long expiresAtEpochSecond
    ) {
    }

    @TestConfiguration
    static class PasswordEncoderTestConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
