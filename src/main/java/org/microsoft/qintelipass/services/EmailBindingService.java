package org.microsoft.qintelipass.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.exceptions.BadRequestException;
import org.microsoft.qintelipass.exceptions.EmailBindingCooldownException;
import org.microsoft.qintelipass.exceptions.EmailBindingDeliveryException;
import org.microsoft.qintelipass.exceptions.NotFoundException;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.response.EmailBindingSendCodeResponse;
import org.microsoft.qintelipass.response.EmailBindingStatusResponse;
import org.microsoft.qintelipass.response.EmailBindingVerifyResponse;
import org.microsoft.qintelipass.util.VerificationCodeGenerator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
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
            return new EmailBindingStatusResponse(true, maskEmail(user.getEmail()), 0);
        }

        String pendingEmail = stringValue(redisService.getValue(pendingKey(userId)));
        long cooldownSeconds = StringUtils.hasText(pendingEmail)
                ? redisService.getExpireSeconds(cooldownKey(pendingEmail))
                : 0;
        return new EmailBindingStatusResponse(false, null, cooldownSeconds);
    }

    public EmailBindingSendCodeResponse sendCode(Long userId, String rawEmail) {
        String email = emailValidationService.normalize(rawEmail);
        User user = requireUser(userId);
        ensureEmailAvailable(user, email);

        String cooldownKey = cooldownKey(email);
        if (!redisService.setIfAbsent(
                cooldownKey,
                String.valueOf(clock.instant().getEpochSecond()),
                Duration.ofSeconds(COOLDOWN_TTL_SECONDS)
        )) {
            throw new EmailBindingCooldownException(
                    Math.max(1, redisService.getExpireSeconds(cooldownKey))
            );
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
            mailService.sendEmailBindingCode(email, code);
            redisService.setValue(
                    codeKey,
                    objectMapper.writeValueAsString(record),
                    Duration.ofSeconds(CODE_TTL_SECONDS)
            );
            redisService.setValue(
                    pendingKey(userId),
                    email,
                    Duration.ofSeconds(CODE_TTL_SECONDS)
            );
        } catch (EmailBindingDeliveryException exception) {
            cleanupFailedSend(userId, email, cooldownKey);
            throw exception;
        } catch (JsonProcessingException | RuntimeException exception) {
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
        if (!userId.equals(record.userId()) || !email.equals(record.email())) {
            throw new BadRequestException(EmailBindingMessages.CODE_INCORRECT);
        }
        if (!passwordEncoder.matches(code, record.codeHash())) {
            throw new BadRequestException(EmailBindingMessages.CODE_INCORRECT);
        }

        ensureEmailAvailable(user, email);
        if (!redisService.deleteIfValueMatches(codeKey, storedVerification.json())) {
            throw new BadRequestException(EmailBindingMessages.CODE_EXPIRED);
        }
        user.setEmail(email);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException(EmailBindingMessages.EMAIL_OCCUPIED);
        }

        deleteVerificationStateAfterCommit(userId, email);
        return new EmailBindingVerifyResponse(true, maskEmail(email));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("当前用户不存在"));
    }

    private void ensureEmailAvailable(User currentUser, String email) {
        if (StringUtils.hasText(currentUser.getEmail())
                && currentUser.getEmail().equalsIgnoreCase(email)) {
            throw new BadRequestException(EmailBindingMessages.EMAIL_ALREADY_BOUND);
        }
        if (userRepository.existsByEmailAndIdNot(email, currentUser.getId())) {
            throw new BadRequestException(EmailBindingMessages.EMAIL_OCCUPIED);
        }
    }

    private StoredVerification readRecord(String key) {
        String json = stringValue(redisService.getValue(key));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return new StoredVerification(
                    json,
                    objectMapper.readValue(json, VerificationRecord.class)
            );
        } catch (JsonProcessingException exception) {
            log.warn("邮箱绑定验证码记录无法解析，已按过期处理");
            safeDelete(key);
            return null;
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
            log.warn("清理邮箱绑定 Redis 状态失败，键前缀：{}", key.substring(0, key.indexOf(':', 6) + 1));
        }
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
