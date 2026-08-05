package org.microsoft.qintelipass.services.auth;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.response.EmailBindingSendCodeResponse;
import org.microsoft.qintelipass.dtos.response.EmailBindingStatusResponse;
import org.microsoft.qintelipass.dtos.response.EmailBindingVerifyResponse;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.EmailBindingCacheException;
import org.microsoft.qintelipass.exceptions.EmailBindingCooldownException;
import org.microsoft.qintelipass.exceptions.EmailBindingDeliveryException;
import org.microsoft.qintelipass.exceptions.EmailBindingPersistenceException;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.services.redis.RedisService;
import org.microsoft.qintelipass.util.VerificationCodeGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
public class EmailBindingService {
    static final long CODE_TTL_SECONDS = 300;
    static final long COOLDOWN_TTL_SECONDS = 60;

    private static final String CODE_KEY_PREFIX = "email:binding:code:";
    private static final String COOLDOWN_KEY_PREFIX = "email:binding:cooldown:";
    private static final String PENDING_KEY_PREFIX = "email:binding:pending:";

    private final UserRepository userRepository;
    private final RedisService redisService;
    private final EmailValidationService emailValidationService;
    private final MailService mailService;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeGenerator verificationCodeGenerator;
    private final Clock clock;

    @Autowired
    public EmailBindingService(
            UserRepository userRepository,
            RedisService redisService,
            EmailValidationService emailValidationService,
            MailService mailService,
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder,
            VerificationCodeGenerator verificationCodeGenerator
    ) {
        this(
                userRepository,
                redisService,
                emailValidationService,
                mailService,
                objectMapper,
                passwordEncoder,
                verificationCodeGenerator,
                Clock.systemUTC()
        );
    }

    EmailBindingService(
            UserRepository userRepository,
            RedisService redisService,
            EmailValidationService emailValidationService,
            MailService mailService,
            ObjectMapper objectMapper,
            PasswordEncoder passwordEncoder,
            VerificationCodeGenerator verificationCodeGenerator,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.redisService = redisService;
        this.emailValidationService = emailValidationService;
        this.mailService = mailService;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeGenerator = verificationCodeGenerator;
        this.clock = clock;
    }

    public EmailBindingStatusResponse getStatus(Long userId) {
        User user = requireUser(userId);
        if (StringUtils.hasText(user.getEmail())) {
            return new EmailBindingStatusResponse(true, maskEmail(user.getEmail()), 0L);
        }

        try {
            String pendingEmail = stringValue(redisService.getValue(pendingKey(userId)));
            long cooldownSeconds = StringUtils.hasText(pendingEmail)
                    ? Math.max(0L, redisService.getExpireSeconds(cooldownKey(pendingEmail)))
                    : 0L;
            return new EmailBindingStatusResponse(false, null, cooldownSeconds);
        } catch (RuntimeException exception) {
            throw new EmailBindingCacheException(exception);
        }
    }

    public EmailBindingSendCodeResponse sendCode(Long userId, String rawEmail) {
        String email = emailValidationService.normalize(rawEmail);
        User user = requireUser(userId);
        ensureEmailAvailable(user, email);

        String cooldownKey = cooldownKey(email);
        boolean acquired;
        try {
            acquired = redisService.setIfAbsent(
                    cooldownKey,
                    String.valueOf(clock.instant().getEpochSecond()),
                    Duration.ofSeconds(COOLDOWN_TTL_SECONDS)
            );
        } catch (RuntimeException exception) {
            throw new EmailBindingCacheException(exception);
        }
        if (!acquired) {
            throw new EmailBindingCooldownException(readCooldown(cooldownKey));
        }

        String codeKey = codeKey(userId, email);
        safeDelete(codeKey);
        safeDelete(pendingKey(userId));

        String code = verificationCodeGenerator.numericCode(6);
        Instant createdAt = clock.instant();
        VerificationRecord record = new VerificationRecord(
                userId,
                email,
                passwordEncoder.encode(code),
                createdAt.getEpochSecond(),
                createdAt.plusSeconds(CODE_TTL_SECONDS).getEpochSecond()
        );

        try {
            String serializedRecord = objectMapper.writeValueAsString(record);
            redisService.setValue(codeKey, serializedRecord, Duration.ofSeconds(CODE_TTL_SECONDS));
            redisService.setValue(pendingKey(userId), email, Duration.ofSeconds(CODE_TTL_SECONDS));
        } catch (JsonProcessingException | RuntimeException exception) {
            cleanupFailedSend(userId, email, cooldownKey);
            throw new EmailBindingCacheException(exception);
        }

        try {
            mailService.sendEmailBindingCode(email, code);
        } catch (EmailBindingDeliveryException exception) {
            cleanupFailedSend(userId, email, cooldownKey);
            throw exception;
        } catch (RuntimeException exception) {
            cleanupFailedSend(userId, email, cooldownKey);
            throw new EmailBindingDeliveryException(exception);
        }
        return new EmailBindingSendCodeResponse(CODE_TTL_SECONDS, COOLDOWN_TTL_SECONDS);
    }

    @Transactional
    public EmailBindingVerifyResponse verifyAndBind(Long userId, String rawEmail, String code) {
        String email = emailValidationService.normalize(rawEmail);
        User user = requireUser(userId);
        ensureEmailAvailable(user, email);

        String codeKey = codeKey(userId, email);
        StoredVerification storedVerification = readRecord(codeKey);
        if (storedVerification == null
                || storedVerification.record().expiresAtEpochSecond() <= clock.instant().getEpochSecond()) {
            safeDelete(codeKey);
            throw new BadRequestException(EmailBindingMessages.CODE_EXPIRED);
        }

        VerificationRecord record = storedVerification.record();
        if (!userId.equals(record.userId())
                || !email.equals(record.email())
                || !passwordEncoder.matches(code, record.codeHash())) {
            throw new BadRequestException(EmailBindingMessages.CODE_INCORRECT);
        }

        ensureEmailAvailable(user, email);
        boolean consumed;
        try {
            consumed = redisService.deleteIfValueMatches(codeKey, storedVerification.json());
        } catch (RuntimeException exception) {
            throw new EmailBindingCacheException(exception);
        }
        if (!consumed) {
            throw new BadRequestException(EmailBindingMessages.CODE_EXPIRED);
        }

        user.setEmail(email);
        user.setUpdatedAt(LocalDateTime.now());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException(EmailBindingMessages.EMAIL_OCCUPIED);
        } catch (DataAccessException exception) {
            throw new EmailBindingPersistenceException(exception);
        }

        deleteVerificationStateAfterCommit(userId, email);
        return new EmailBindingVerifyResponse(true, maskEmail(email));
    }

    private User requireUser(Long userId) {
        try {
            return userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("当前用户不存在"));
        } catch (DataAccessException exception) {
            throw new EmailBindingPersistenceException(exception);
        }
    }

    private void ensureEmailAvailable(User currentUser, String email) {
        if (StringUtils.hasText(currentUser.getEmail())) {
            throw new BadRequestException(EmailBindingMessages.EMAIL_ALREADY_BOUND);
        }
        try {
            if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, currentUser.getId())) {
                throw new BadRequestException(EmailBindingMessages.EMAIL_OCCUPIED);
            }
        } catch (DataAccessException exception) {
            throw new EmailBindingPersistenceException(exception);
        }
    }

    private StoredVerification readRecord(String key) {
        String json;
        try {
            json = stringValue(redisService.getValue(key));
        } catch (RuntimeException exception) {
            throw new EmailBindingCacheException(exception);
        }
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return new StoredVerification(json, objectMapper.readValue(json, VerificationRecord.class));
        } catch (JsonProcessingException exception) {
            log.warn("邮箱绑定验证码记录无法解析，已按过期处理");
            safeDelete(key);
            return null;
        }
    }

    private long readCooldown(String cooldownKey) {
        try {
            return Math.max(1L, redisService.getExpireSeconds(cooldownKey));
        } catch (RuntimeException exception) {
            throw new EmailBindingCacheException(exception);
        }
    }

    private void deleteVerificationStateAfterCommit(Long userId, String email) {
        Runnable cleanup = () -> {
            safeDelete(codeKey(userId, email));
            safeDelete(cooldownKey(email));
            safeDelete(pendingKey(userId));
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    private void cleanupFailedSend(Long userId, String email, String cooldownKey) {
        safeDelete(codeKey(userId, email));
        safeDelete(pendingKey(userId));
        safeDelete(cooldownKey);
    }

    private void safeDelete(String key) {
        try {
            redisService.deleteValue(key);
        } catch (RuntimeException exception) {
            log.warn("清理邮箱绑定 Redis 状态失败，键类型：{}", keyType(key));
        }
    }

    private String keyType(String key) {
        if (key.startsWith(CODE_KEY_PREFIX)) {
            return "code";
        }
        if (key.startsWith(COOLDOWN_KEY_PREFIX)) {
            return "cooldown";
        }
        return "pending";
    }

    private String codeKey(Long userId, String email) {
        return CODE_KEY_PREFIX + userId + ":" + emailDigest(email);
    }

    private String cooldownKey(String email) {
        return COOLDOWN_KEY_PREFIX + emailDigest(email);
    }

    private String pendingKey(Long userId) {
        return PENDING_KEY_PREFIX + userId;
    }

    private String emailDigest(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return "***";
        }
        return email.substring(0, 1) + "***" + email.substring(atIndex);
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private record VerificationRecord(
            Long userId,
            String email,
            String codeHash,
            long createdAtEpochSecond,
            long expiresAtEpochSecond
    ) {
    }

    private record StoredVerification(String json, VerificationRecord record) {
    }
}