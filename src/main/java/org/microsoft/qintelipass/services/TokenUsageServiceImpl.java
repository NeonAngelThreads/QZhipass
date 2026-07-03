package org.microsoft.qintelipass.services;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.TokenUsageRankDTO;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.models.*;
import org.microsoft.qintelipass.repository.DailyConfigRepository;
import org.microsoft.qintelipass.repository.ModelsRepository;
import org.microsoft.qintelipass.repository.TokenDailySummaryRepository;
import org.microsoft.qintelipass.repository.TokenUsageLogRepository;
import org.microsoft.qintelipass.util.ExpirationTimeHelper;
import org.microsoft.qintelipass.util.Snowflake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class TokenUsageServiceImpl implements TokenUsageService {
    private static final String USAGE_KEY_PREFIX = "usage:daily:";
    private static final String RANK_KEY_PREFIX = "usage:daily:rank:";
    private static final String LIMIT_KEY_PREFIX = "user:token:limit:";
    private static final String TOTAL_TOKENS_KEY = "models:daily:total:tokens";
    private static final String MODEL_TOTAL_KEY_PREFIX = "models:daily:total:";
    private static long DEFAULT_TOKEN_LIMIT = 100000L;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final DailyConfigRepository dailyConfigRepository;
    private final TokenUsageLogRepository tokenUsageLogRepository;
    private final TokenDailySummaryRepository tokenDailySummaryRepository;
    private final ModelsRepository modelsRepository;
    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    @Autowired
    public TokenUsageServiceImpl(RedisTemplate<String, String> redisTemplate,
                                  UserService userService,
                                  DailyConfigRepository dailyConfigRepository,
                                  TokenUsageLogRepository tokenUsageLogRepository,
                                  TokenDailySummaryRepository tokenDailySummaryRepository,
                                  ModelsRepository modelsRepository) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
        this.dailyConfigRepository = dailyConfigRepository;
        this.tokenUsageLogRepository = tokenUsageLogRepository;
        this.tokenDailySummaryRepository = tokenDailySummaryRepository;
        this.modelsRepository = modelsRepository;
    }

    @Override
    @Transactional
    public boolean recordTokenUsage(Long userId, Long modelId, int tokensUsed) {
        if (tokensUsed <= 0 || userId == null) {
            return false;
        }

        String today = getTodayDateString();
        String usageKey = getUsageKey(today, userId);
        String rankKey = getRankKey(today);
        String modelTotalKey = MODEL_TOTAL_KEY_PREFIX + modelId + ":" + today;

        Long currentUsage = redisTemplate.opsForValue().increment(usageKey, tokensUsed);
        if (currentUsage != null && currentUsage == tokensUsed) {
            redisTemplate.expireAt(usageKey, ExpirationTimeHelper.getNextDayTime());
        }

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();
        zSetOps.incrementScore(rankKey, String.valueOf(userId), tokensUsed);
        if (zSetOps.size(rankKey) != null && zSetOps.size(rankKey) == 1) {
            redisTemplate.expireAt(rankKey, ExpirationTimeHelper.getNextDayTime());
        }

        redisTemplate.opsForValue().increment(modelTotalKey, tokensUsed);
        redisTemplate.expireAt(modelTotalKey, ExpirationTimeHelper.getNextDayTime());

        TokenUsageLog logEntry = TokenUsageLog.builder()
                .userId(userId)
                .modelId(modelId != null ? modelId : 1L)
                .id(Snowflake.nextId())
                .tokensUsed(tokensUsed)
                .usageDate(LocalDate.now())
                .createdAt(OffsetDateTime.now())
                .build();
        tokenUsageLogRepository.save(logEntry);

        log.debug("Recorded token usage: userId={}, modelId={}, tokens={}, total={}", userId, modelId, tokensUsed, currentUsage);
        return true;
    }

    @Override
    public boolean checkTokenLimit(Long userId) {
        long limit = getUserTokenLimit(userId);
        long currentUsage = getCurrentTokenUsage(userId);
        boolean exceeded = currentUsage >= limit;

        if (exceeded) {
            log.warn("User {} exceeded token limit: usage={}, limit={}", userId, currentUsage, limit);
        }

        return !exceeded;
    }

    @Override
    public UserTokenUsageDTO getUserTokenUsage(Long userId) {
        User user = userService.getUserById(userId);
        String userName = user != null ? user.getName() : "Unknown";
        String department = user != null ? user.getDepartment() : "Unknown";

        long currentUsage = getCurrentTokenUsage(userId);
        long limit = getUserTokenLimit(userId);

        return UserTokenUsageDTO.builder()
                .userId(userId)
                .userName(userName)
                .tokenUsed(currentUsage)
                .tokenLimit(limit)
                .isExceeded(currentUsage >= limit)
                .build();
    }

    @Override
    public List<TokenUsageRankDTO> getDailyTokenRank(int topN) {
        String today = getTodayDateString();
        String rankKey = getRankKey(today);

        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(rankKey, 0, topN - 1);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        int rank = 1;
        List<TokenUsageRankDTO> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long userId = Long.valueOf(tuple.getValue());
            Double score = tuple.getScore();
            Long totalTokens = score != null ? score.longValue() : 0L;

            User user = userService.getUserById(userId);
            String userName = user != null ? user.getName() : "Unknown";

            result.add(TokenUsageRankDTO.builder()
                    .userId(userId)
                    .userName(userName)
                    .totalTokens(totalTokens)
                    .rank(rank++)
                    .build());
        }

        return result;
    }

    @Override
    public long getUserTokenLimit(Long userId) {
        String limitKey = LIMIT_KEY_PREFIX + userId;
        String limitStr = redisTemplate.opsForValue().get(limitKey);

        if (limitStr != null) {
            try {
                return Long.parseLong(limitStr);
            } catch (NumberFormatException e) {
                log.error("Invalid token limit format for user: {}", userId);
            }
        }

        Optional<DailyConfig> config = dailyConfigRepository.findByUserId(userId);
        if (config.isPresent()) {
            return config.get().getDailyLimit();
        }

        return DEFAULT_TOKEN_LIMIT;
    }

    @Override
    public void setUserTokenLimit(Long userId, long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Token limit must be positive");
        }
        String limitKey = LIMIT_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(limitKey, String.valueOf(limit));

        Optional<DailyConfig> existingConfig = dailyConfigRepository.findByUserId(userId);
        if (existingConfig.isPresent()) {
            DailyConfig config = existingConfig.get();
            config.setDailyLimit(limit);
            dailyConfigRepository.save(config);
        } else {
            DailyConfig config = DailyConfig.builder()
                    .userId(userId)
                    .dailyLimit(limit)
                    .build();
            dailyConfigRepository.save(config);
        }

        log.info("Set token limit: userId={}, limit={}", userId, limit);
    }

    @Override
    public String getTodayTotalTokens() {
        return Optional
                .ofNullable(this.redisTemplate.opsForValue().get(TOTAL_TOKENS_KEY))
                .orElse("0");
    }

    @Override
    public void increaseDailyTotalTokens(Integer tokens) {
        this.redisTemplate.opsForValue().increment(TOTAL_TOKENS_KEY, tokens);
        this.redisTemplate.expireAt(TOTAL_TOKENS_KEY, ExpirationTimeHelper.getNextDayTime());
    }

    @Override
    public Long getOveruseUsers() {
        String rankKey = getRankKey();
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(rankKey, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            return 0L;
        }

        long overuseCount = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long userId = Long.valueOf(tuple.getValue());
            Double score = tuple.getScore();
            long usage = score != null ? score.longValue() : 0L;
            long limit = getUserTokenLimit(userId);
            if (usage >= limit) {
                overuseCount++;
            }
        }
        return overuseCount;
    }

    @Override
    public Long getDailyTokenLimit() {
        return DEFAULT_TOKEN_LIMIT;
    }

    @Override
    public void setDailyTokenLimit(Long value) {
        DEFAULT_TOKEN_LIMIT = value;
    }

    @Override
    public Map<String, Object> getModelStatisticsForLast7Days() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6);

        List<TokenDailySummary> summaries = tokenDailySummaryRepository.findByUsageDateBetween(startDate, today);

        Map<String, Map<String, Long>> modelDailyStats = new HashMap<>();
        Set<String> modelIds = new HashSet<>();
        List<String> dates = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            dates.add(today.minusDays(6 - i).format(DATE_FORMATTER));
        }

        for (TokenDailySummary summary : summaries) {
            String modelKey = String.valueOf(summary.getModelId());
            modelIds.add(modelKey);
            String dateStr = summary.getUsageDate().format(DATE_FORMATTER);

            modelDailyStats.computeIfAbsent(modelKey, k -> new HashMap<>());
            modelDailyStats.get(modelKey).put(dateStr, summary.getTotalTokens());
        }

        String todayStr = today.format(DATE_FORMATTER);
        String redisPattern = MODEL_TOTAL_KEY_PREFIX + "*:" + todayStr;
        Set<String> todayModelKeys = redisTemplate.keys(redisPattern);
        if (todayModelKeys != null) {
            for (String key : todayModelKeys) {
                String modelId = key.substring(MODEL_TOTAL_KEY_PREFIX.length(), key.lastIndexOf(":" + todayStr));
                modelIds.add(modelId);
                String todayTokens = redisTemplate.opsForValue().get(key);
                if (todayTokens != null) {
                    modelDailyStats.computeIfAbsent(modelId, k -> new HashMap<>());
                    modelDailyStats.get(modelId).put(todayStr, Long.parseLong(todayTokens));
                }
            }
        }

        List<Map<String, Object>> modelStatsList = new ArrayList<>();
        for (String modelId : modelIds) {
            Map<String, Object> modelStat = new HashMap<>();
            modelStat.put("modelId", Long.parseLong(modelId));
            modelStat.put("modelName", getModelName(Long.parseLong(modelId)));

            List<Map<String, Object>> dailyUsage = new ArrayList<>();
            for (String date : dates) {
                Map<String, Object> dayStat = new HashMap<>();
                dayStat.put("date", date);
                dayStat.put("tokens", modelDailyStats.getOrDefault(modelId, new HashMap<>()).getOrDefault(date, 0L));
                dailyUsage.add(dayStat);
            }
            modelStat.put("dailyUsage", dailyUsage);
            modelStatsList.add(modelStat);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("models", modelStatsList);
        result.put("dates", dates);

        return result;
    }

    @Override
    public Long getActiveUserCount() {
        LocalDate today = LocalDate.now();
        List<Long> userIds = tokenUsageLogRepository.findDistinctUserIdsByDate(today);
        return (long) userIds.size();
    }

    @Override
    public Map<String, Object> getDepartmentStatistics() {
        LocalDate today = LocalDate.now();
        List<Object[]> userUsageData = tokenUsageLogRepository.sumByUserIdForDate(today);

        Map<String, Long> departmentUsage = new HashMap<>();
        Map<String, Long> departmentUserCount = new HashMap<>();

        for (Object[] row : userUsageData) {
            Long userId = (Long) row[0];
            Long tokens = (Long) row[1];

            User user = userService.getUserById(userId);
            if (user != null) {
                String department = user.getDepartment() != null ? user.getDepartment() : "未分配";
                departmentUsage.merge(department, tokens, Long::sum);
                departmentUserCount.merge(department, 1L, Long::sum);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("departmentUsage", departmentUsage);
        result.put("departmentUserCount", departmentUserCount);

        return result;
    }

    @Override
    public List<Map<String, Object>> getAllUserTokenUsage() {
        LocalDate today = LocalDate.now();
        List<Object[]> userUsageData = tokenUsageLogRepository.sumByUserIdForDate(today);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : userUsageData) {
            Long userId = (Long) row[0];
            Long tokens = (Long) row[1];

            User user = userService.getUserById(userId);
            if (user != null) {
                long limit = getUserTokenLimit(userId);
                Map<String, Object> userStat = new HashMap<>();
                userStat.put("userId", userId);
                userStat.put("userName", user.getName());
                userStat.put("department", user.getDepartment());
                userStat.put("tokensUsed", tokens);
                userStat.put("tokenLimit", limit);
                userStat.put("isExceeded", tokens >= limit);
                result.add(userStat);
            }
        }

        result.sort((a, b) -> Long.compare((Long) b.get("tokensUsed"), (Long) a.get("tokensUsed")));
        return result;
    }

    @Transactional
    public void aggregateDailyData() {
        LocalDate today = LocalDate.now();
        List<Object[]> modelData = tokenUsageLogRepository.sumByModelIdForDate(today);

        for (Object[] row : modelData) {
            Long modelId = (Long) row[0];
            Long totalTokens = (Long) row[1];

            Optional<TokenDailySummary> existingSummary = tokenDailySummaryRepository.findByUsageDateAndModelId(today, modelId);
            if (existingSummary.isPresent()) {
                TokenDailySummary summary = existingSummary.get();
                summary.setTotalTokens(totalTokens);
                tokenDailySummaryRepository.save(summary);
            } else {
                TokenDailySummary summary = TokenDailySummary.builder()
                        .usageDate(today)
                        .modelId(modelId)
                        .totalTokens(totalTokens)
                        .build();
                tokenDailySummaryRepository.save(summary);
            }
        }

        log.info("Daily token usage aggregated for date: {}", today);
    }

    private long getCurrentTokenUsage(Long userId) {
        String today = getTodayDateString();
        String usageKey = getUsageKey(today, userId);
        String usageStr = redisTemplate.opsForValue().get(usageKey);

        if (usageStr == null) {
            return 0L;
        }

        try {
            return Long.parseLong(usageStr);
        } catch (NumberFormatException e) {
            log.error("Invalid token usage format for user: {}", userId);
            return 0L;
        }
    }

    private String getTodayDateString() {
        return LocalDate.now().format(DATE_FORMATTER);
    }

    private String getUsageKey(String date, Long userId) {
        return USAGE_KEY_PREFIX + date + ":" + userId;
    }

    private String getRankKey(String date) {
        return RANK_KEY_PREFIX + date;
    }

    private String getRankKey() {
        return RANK_KEY_PREFIX + getTodayDateString();
    }

    private String getModelName(Long modelId) {
        return modelsRepository.findById(modelId)
                .map(Models::getModelName)
                .orElse("Model-" + modelId);
    }
}