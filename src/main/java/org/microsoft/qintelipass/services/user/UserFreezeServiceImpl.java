package org.microsoft.qintelipass.services.user;

import org.microsoft.qintelipass.dtos.UserFreezeLogDTO;
import org.microsoft.qintelipass.entity.User;
import org.microsoft.qintelipass.entity.UserFreezeLog;
import org.microsoft.qintelipass.enums.UserFreezeAction;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.repository.CensorAlertRepository;
import org.microsoft.qintelipass.repository.UserFreezeLogRepository;
import org.microsoft.qintelipass.repository.UserRepository;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.microsoft.qintelipass.services.UserFreezeService;
import org.microsoft.qintelipass.services.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class UserFreezeServiceImpl implements UserFreezeService {
    private static final String UNFREEZE_NOTICE =
            "\u60a8\u7684\u8d26\u6237\u5df2\u89e3\u51bb";

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserFreezeLogRepository freezeLogRepository;
    private final CensorAlertRepository censorAlertRepository;
    private final TokenUsageService tokenService;

    public UserFreezeServiceImpl(
            UserRepository userRepository,
            UserService userService,
            UserFreezeLogRepository freezeLogRepository,
            CensorAlertRepository censorAlertRepository,
            TokenUsageService tokenService
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.freezeLogRepository = freezeLogRepository;
        this.censorAlertRepository = censorAlertRepository;
        this.tokenService = tokenService;
    }

    @Override
    @Transactional
    public UserFreezeLogDTO freezeUser(
            Long userId,
            String reason,
            Long censorAlertId,
            Long operatorId,
            String operatorName
    ) {
        String normalizedReason = requireReason(reason);
        User user = requireUser(userId);
        if (UserStatus.CANCELLED.equals(user.getStatus())) {
            throw new IllegalArgumentException("Cancelled users cannot be frozen.");
        }
        if (censorAlertId != null && !censorAlertRepository.existsById(censorAlertId)) {
            throw new IllegalArgumentException("Censor alert does not exist.");
        }

        long previousLimit = tokenService.getUserQuota(user.getId());
        user.setStatus(UserStatus.FROZEN);
        userService.saveUser(user);
        tokenService.setUserQuota(user.getId(), 0L);

        UserFreezeLog log = buildBaseLog(
                user,
                UserFreezeAction.FREEZE,
                normalizedReason,
                operatorId,
                operatorName
        );
        log.setCensorAlertId(censorAlertId);
        log.setPreviousTokenLimit(previousLimit);
        return UserFreezeLogDTO.fromEntity(freezeLogRepository.save(log));
    }

    @Override
    @Transactional
    public UserFreezeLogDTO unfreezeUser(
            Long userId,
            String reason,
            Long operatorId,
            String operatorName
    ) {
        String normalizedReason = requireReason(reason);
        User user = requireUser(userId);
        if (UserStatus.CANCELLED.equals(user.getStatus())) {
            throw new IllegalArgumentException("Cancelled users cannot be unfrozen.");
        }

        Long restoredLimit = freezeLogRepository
                .findFirstByUser_IdAndActionOrderByOperatedAtDesc(
                        user.getId(),
                        UserFreezeAction.FREEZE
                )
                .map(UserFreezeLog::getPreviousTokenLimit)
                .orElse(tokenService.getGlobalQuota());

        user.setStatus(UserStatus.NORMAL);
        userService.saveUser(user);
        tokenService.setUserQuota(user.getId(), restoredLimit);

        UserFreezeLog log = buildBaseLog(
                user,
                UserFreezeAction.UNFREEZE,
                normalizedReason,
                operatorId,
                operatorName
        );
        log.setPreviousTokenLimit(restoredLimit);
        log.setNotificationMessage(UNFREEZE_NOTICE);
        return UserFreezeLogDTO.fromEntity(freezeLogRepository.save(log));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserFreezeLogDTO> getFreezeLogs(Long userId) {
        requireUser(userId);
        return freezeLogRepository.findByUser_IdOrderByOperatedAtDesc(userId)
                .stream()
                .map(UserFreezeLogDTO::fromEntity)
                .toList();
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }

    private String requireReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("Reason is required.");
        }
        return reason.trim();
    }

    private UserFreezeLog buildBaseLog(
            User user,
            UserFreezeAction action,
            String reason,
            Long operatorId,
            String operatorName
    ) {
        UserFreezeLog log = new UserFreezeLog();
        log.setUser(user);
        log.setUserName(defaultText(user.getName(), String.valueOf(user.getId())));
        log.setDepartment(user.getDepartment());
        log.setOperatorId(operatorId);
        log.setOperatorName(defaultText(operatorName, "UNKNOWN_ADMIN"));
        log.setAction(action);
        log.setReason(reason);
        return log;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}