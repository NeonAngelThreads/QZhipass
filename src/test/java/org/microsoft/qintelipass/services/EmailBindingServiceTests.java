package org.microsoft.qintelipass.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.EmailBindingCacheException;
import org.microsoft.qintelipass.exceptions.EmailBindingCooldownException;
import org.microsoft.qintelipass.exceptions.EmailBindingDeliveryException;
import org.microsoft.qintelipass.exceptions.EmailBindingPersistenceException;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.response.EmailBindingSendCodeResponse;
import org.microsoft.qintelipass.response.EmailBindingStatusResponse;
import org.microsoft.qintelipass.response.EmailBindingVerifyResponse;
import org.microsoft.qintelipass.util.VerificationCodeGenerator;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailBindingServiceTests {
    private static final long USER_ID = 101L;
    private static final String EMAIL = "employee@company.com";
    private static final String CODE = "804271";
    private static final String CODE_HASH = "secure-hash";
    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private RedisService redisService;
    @Mock private EmailValidationService emailValidationService;
    @Mock private MailService mailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationCodeGenerator verificationCodeGenerator;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User user;
    private EmailBindingService service;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email(null).build();
        service = new EmailBindingService(
                userRepository,
                redisService,
                emailValidationService,
                mailService,
                objectMapper,
                passwordEncoder,
                verificationCodeGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        lenient().when(emailValidationService.normalize(anyString())).thenReturn(EMAIL);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void returnsBoundStatusWithMaskedEmail() {
        user.setEmail(EMAIL);

        EmailBindingStatusResponse response = service.getStatus(USER_ID);

        assertThat(response.bound()).isTrue();
        assertThat(response.email()).isEqualTo("e***@company.com");
        assertThat(response.cooldownSeconds()).isZero();
        verifyNoInteractions(redisService);
    }

    @Test
    void returnsServerCooldownForUnboundStatus() {
        when(redisService.getValue("email:binding:pending:" + USER_ID)).thenReturn(EMAIL);
        when(redisService.getExpireSeconds(startsWith("email:binding:cooldown:"))).thenReturn(37L);

        EmailBindingStatusResponse response = service.getStatus(USER_ID);

        assertThat(response.bound()).isFalse();
        assertThat(response.email()).isNull();
        assertThat(response.cooldownSeconds()).isEqualTo(37L);
    }

    @Test
    void sendsCodeWithAtomicCooldownAndStoresOnlyHashForFiveMinutes() {
        when(redisService.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(true);
        when(verificationCodeGenerator.numericCode(6)).thenReturn(CODE);
        when(passwordEncoder.encode(CODE)).thenReturn(CODE_HASH);

        EmailBindingSendCodeResponse response = service.sendCode(USER_ID, EMAIL);

        assertThat(response.expiresInSeconds()).isEqualTo(300L);
        assertThat(response.cooldownSeconds()).isEqualTo(60L);
        verify(mailService).sendEmailBindingCode(EMAIL, CODE);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(redisService, times(2)).setValue(
                keyCaptor.capture(),
                valueCaptor.capture(),
                ttlCaptor.capture()
        );
        List<String> keys = keyCaptor.getAllValues();
        int codeRecordIndex = keys.get(0).startsWith("email:binding:code:") ? 0 : 1;
        String codeRecord = valueCaptor.getAllValues().get(codeRecordIndex);
        assertThat(codeRecord).contains(CODE_HASH, EMAIL, String.valueOf(USER_ID));
        assertThat(codeRecord).doesNotContain("\"" + CODE + "\"");
        assertThat(ttlCaptor.getAllValues().get(codeRecordIndex)).isEqualTo(Duration.ofSeconds(300));
    }

    @Test
    void blocksRepeatedSendWithRemainingSeconds() {
        when(redisService.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(false);
        when(redisService.getExpireSeconds(anyString())).thenReturn(42L);

        EmailBindingCooldownException exception = assertThrows(
                EmailBindingCooldownException.class,
                () -> service.sendCode(USER_ID, " Employee@Company.com ")
        );

        assertThat(exception.getCooldownSeconds()).isEqualTo(42L);
        verifyNoInteractions(mailService);
    }

    @Test
    void removesVerificationStateWhenMailDeliveryFails() {
        when(redisService.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(true);
        when(verificationCodeGenerator.numericCode(6)).thenReturn(CODE);
        when(passwordEncoder.encode(CODE)).thenReturn(CODE_HASH);
        org.mockito.Mockito.doThrow(new EmailBindingDeliveryException())
                .when(mailService)
                .sendEmailBindingCode(EMAIL, CODE);

        assertThrows(
                EmailBindingDeliveryException.class,
                () -> service.sendCode(USER_ID, EMAIL)
        );
        verify(redisService, atLeastOnce()).deleteValue(startsWith("email:binding:code:"));
        verify(redisService, atLeastOnce()).deleteValue(startsWith("email:binding:pending:"));
        verify(redisService).deleteValue(startsWith("email:binding:cooldown:"));
    }

    @Test
    void reportsRedisStorageFailureWithoutPretendingDeliverySucceeded() {
        when(redisService.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(true);
        when(verificationCodeGenerator.numericCode(6)).thenReturn(CODE);
        when(passwordEncoder.encode(CODE)).thenReturn(CODE_HASH);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis unavailable"))
                .when(redisService)
                .setValue(
                        startsWith("email:binding:pending:"),
                        eq(EMAIL),
                        eq(Duration.ofSeconds(300))
                );

        assertThrows(
                EmailBindingCacheException.class,
                () -> service.sendCode(USER_ID, EMAIL)
        );
        verifyNoInteractions(mailService);
    }

    @Test
    void blocksSendingWhenCurrentAccountAlreadyHasAnyEmail() {
        user.setEmail("existing@company.com");

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.sendCode(USER_ID, EMAIL)
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.EMAIL_ALREADY_BOUND);
        verify(redisService, never()).setIfAbsent(anyString(), anyString(), any());
    }

    @Test
    void blocksSendingWhenAnotherAccountOwnsEmailIgnoringCase() {
        when(userRepository.existsByEmailIgnoreCaseAndIdNot(EMAIL, USER_ID)).thenReturn(true);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.sendCode(USER_ID, EMAIL)
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.EMAIL_OCCUPIED);
        verify(redisService, never()).setIfAbsent(anyString(), anyString(), any());
    }

    @Test
    void rejectsExpiredOrMissingCode() {
        when(redisService.getValue(startsWith("email:binding:code:"))).thenReturn(null);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.verifyAndBind(USER_ID, EMAIL, CODE)
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.CODE_EXPIRED);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsWrongCodeWithoutConsumingStoredRecord() throws Exception {
        when(redisService.getValue(startsWith("email:binding:code:")))
                .thenReturn(verificationRecord(NOW.plusSeconds(300)));
        when(passwordEncoder.matches(CODE, CODE_HASH)).thenReturn(false);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.verifyAndBind(USER_ID, EMAIL, CODE)
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.CODE_INCORRECT);
        verify(redisService, never()).deleteIfValueMatches(anyString(), anyString());
    }

    @Test
    void bindsExistingUserEmailAndConsumesCodeAtomically() throws Exception {
        String record = verificationRecord(NOW.plusSeconds(300));
        when(redisService.getValue(startsWith("email:binding:code:"))).thenReturn(record);
        when(passwordEncoder.matches(CODE, CODE_HASH)).thenReturn(true);
        when(redisService.deleteIfValueMatches(anyString(), eq(record))).thenReturn(true);
        when(userRepository.saveAndFlush(user)).thenReturn(user);

        EmailBindingVerifyResponse response = service.verifyAndBind(USER_ID, EMAIL, CODE);

        assertThat(response.bound()).isTrue();
        assertThat(response.email()).isEqualTo("e***@company.com");
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void reportsConcurrentUniqueConflictAsOccupied() throws Exception {
        String record = verificationRecord(NOW.plusSeconds(300));
        when(redisService.getValue(startsWith("email:binding:code:"))).thenReturn(record);
        when(passwordEncoder.matches(CODE, CODE_HASH)).thenReturn(true);
        when(redisService.deleteIfValueMatches(anyString(), eq(record))).thenReturn(true);
        when(userRepository.saveAndFlush(user))
                .thenThrow(new DataIntegrityViolationException("unique email"));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.verifyAndBind(USER_ID, EMAIL, CODE)
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.EMAIL_OCCUPIED);
        assertThat(exception.getMessage()).doesNotContain("unique");
    }

    @Test
    void reportsDatabaseFailureWithStablePublicMessage() throws Exception {
        String record = verificationRecord(NOW.plusSeconds(300));
        when(redisService.getValue(startsWith("email:binding:code:"))).thenReturn(record);
        when(passwordEncoder.matches(CODE, CODE_HASH)).thenReturn(true);
        when(redisService.deleteIfValueMatches(anyString(), eq(record))).thenReturn(true);
        when(userRepository.saveAndFlush(user))
                .thenThrow(new DataAccessResourceFailureException("database offline"));

        EmailBindingPersistenceException exception = assertThrows(
                EmailBindingPersistenceException.class,
                () -> service.verifyAndBind(USER_ID, EMAIL, CODE)
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.PERSISTENCE_FAILED);
        assertThat(exception.getMessage()).doesNotContain("offline");
    }

    @Test
    void sameVerificationRecordCannotBeConsumedTwice() throws Exception {
        User firstAttemptUser = User.builder().id(USER_ID).build();
        User secondAttemptUser = User.builder().id(USER_ID).build();
        String record = verificationRecord(NOW.plusSeconds(300));
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(firstAttemptUser), Optional.of(secondAttemptUser));
        when(redisService.getValue(startsWith("email:binding:code:"))).thenReturn(record);
        when(passwordEncoder.matches(CODE, CODE_HASH)).thenReturn(true);
        when(redisService.deleteIfValueMatches(anyString(), eq(record))).thenReturn(true, false);
        when(userRepository.saveAndFlush(firstAttemptUser)).thenReturn(firstAttemptUser);

        assertThat(service.verifyAndBind(USER_ID, EMAIL, CODE).bound()).isTrue();
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.verifyAndBind(USER_ID, EMAIL, CODE)
        );

        assertThat(exception.getMessage()).isEqualTo(EmailBindingMessages.CODE_EXPIRED);
        verify(userRepository, times(1)).saveAndFlush(any());
    }

    private String verificationRecord(Instant expiresAt) throws Exception {
        return objectMapper.writeValueAsString(new VerificationRecordForTest(
                USER_ID,
                EMAIL,
                CODE_HASH,
                NOW.getEpochSecond(),
                expiresAt.getEpochSecond()
        ));
    }

    private record VerificationRecordForTest(
            Long userId,
            String email,
            String codeHash,
            long createdAtEpochSecond,
            long expiresAtEpochSecond
    ) {
    }
}
