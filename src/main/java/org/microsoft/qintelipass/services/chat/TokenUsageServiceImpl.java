package org.microsoft.qintelipass.services.chat;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.ai.token.DepartmentUsageData;
import org.microsoft.qintelipass.dtos.TokenUsageRankDTO;
import org.microsoft.qintelipass.dtos.UserTokenUsageDTO;
import org.microsoft.qintelipass.entity.*;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.repository.*;
import org.microsoft.qintelipass.services.TokenUsageService;
import org.microsoft.qintelipass.services.UserService;
import org.microsoft.qintelipass.util.ExpirationTimeHelper;
import org.microsoft.qintelipass.util.Snowflake;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TokenUsageServiceImpl implements TokenUsageService {
    private static final String USAGE_KEY_PREFIX = "usage:daily:";
    private static final String RANK_KEY_PREFIX = "usage:daily:rank:";
    private static final String LIMIT_KEY_PREFIX = "user:token:limit:";
    private static final String TOTAL_TOKENS_KEY = "models:daily:total:tokens";
    private static final String MODEL_TOTAL_KEY_PREFIX = "models:daily:total:";
    private static final String MODEL_NAME_KEY_PREFIX = "models:name:";

    private static final String GLOBAL_QUOTA_KEY = "global_token_quota";
    private static final String USER_QUOTA_KEY_PREFIX = "user_quota_";
    private static final long DEFAULT_QUOTA = 100_000L;

    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    private final DailyConfigRepository dailyConfigRepository;
    private final TokenUsageLogRepository tokenUsageLogRepository;
    private final TokenDailySummaryRepository tokenDailySummaryRepository;
    private final ModelsRepository modelsRepository;
    private final GlobalConfigRepository globalConfigRepository;
    private final UserRepository userRepository;

    private final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public TokenUsageServiceImpl(RedisTemplate<String, String> redisTemplate,
                                  UserService userService,
                                  DailyConfigRepository dailyConfigRepository,
                                  TokenUsageLogRepository tokenUsageLogRepository,
                                  TokenDailySummaryRepository tokenDailySummaryRepository,
                                  ModelsRepository modelsRepository,
                                  GlobalConfigRepository globalConfigRepository,
                                  UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.userService = userService;
        this.dailyConfigRepository = dailyConfigRepository;
        this.tokenUsageLogRepository = tokenUsageLogRepository;
        this.tokenDailySummaryRepository = tokenDailySummaryRepository;
        this.modelsRepository = modelsRepository;
        this.globalConfigRepository = globalConfigRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void initDefaultQuota() {
        if (globalConfigRepository.findById(GLOBAL_QUOTA_KEY).isEmpty()) {
            globalConfigRepository.save(new GlobalConfig(
                    GLOBAL_QUOTA_KEY,
                    String.valueOf(DEFAULT_QUOTA)
            ));
        }
    }

    @Override
    @Transactional
    public boolean recordTokenUsage(User user, Models model, int tokensUsed) {
        if (tokensUsed <= 0 || user == null || model == null) {
            return false;
        }

        Long userId = user.getId();
        Long modelId = model.getId();
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

        redisTemplate.opsForValue().increment(TOTAL_TOKENS_KEY, tokensUsed);
        redisTemplate.expireAt(TOTAL_TOKENS_KEY, ExpirationTimeHelper.getNextDayTime());

        TokenUsageLog logEntry = TokenUsageLog.builder()
                .id(Snowflake.nextId())
                .user(user)
                .model(model)
                .tokensUsed(tokensUsed)
                .usageDate(LocalDate.now())
                .build();
        tokenUsageLogRepository.save(logEntry);

        log.debug("Recorded token usage: userId={}, modelId={}, tokens={}, total={}", userId, modelId, tokensUsed, currentUsage);
        return true;
    }

    @Override
    public boolean checkTokenLimit(User user) {
        long limit = getUserTokenLimit(user);
        long currentUsage = getCurrentTokenUsage(user.getId());
        boolean exceeded = currentUsage >= limit;

        if (exceeded) {
            log.warn("User {} exceeded token limit: usage={}, limit={}", user.getId(), currentUsage, limit);
        }

        return !exceeded;
    }

    @Override
    public UserTokenUsageDTO getUserTokenUsage(User user) {
        String userName = user.getName();

        long currentUsage = getCurrentTokenUsage(user.getId());
        long limit = getUserTokenLimit(user);

        return UserTokenUsageDTO.builder()
                .userId(user.getId())
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

            User u = userService.getUserById(userId);
            String userName = u != null ? u.getName() : "Unknown";

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
    public long getUserTokenLimit(User user) {
        Long userId = user.getId();
        String limitKey = LIMIT_KEY_PREFIX + userId;
        String limitStr = redisTemplate.opsForValue().get(limitKey);

        if (limitStr != null) {
            try {
                return Long.parseLong(limitStr);
            } catch (NumberFormatException e) {
                log.error("Invalid token limit format for user: {}", userId);
            }
        }

        Optional<GlobalConfig> userQuota = globalConfigRepository.findById(USER_QUOTA_KEY_PREFIX + userId);
        if (userQuota.isPresent()) {
            try {
                return Long.parseLong(userQuota.get().getValue());
            } catch (NumberFormatException ignored) {
            }
        }

        Optional<DailyConfig> config = dailyConfigRepository.findByUser_Id(userId);
        if (config.isPresent()) {
            return config.get().getDailyLimit();
        }

        return getGlobalQuota();
    }

    @Override
    public void setUserTokenLimit(User user, long limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Token limit must be positive");
        }
        Long userId = user.getId();
        String limitKey = LIMIT_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(limitKey, String.valueOf(limit));

        Optional<DailyConfig> existingConfig = dailyConfigRepository.findByUser_Id(userId);
        if (existingConfig.isPresent()) {
            DailyConfig config = existingConfig.get();
            config.setDailyLimit(limit);
            dailyConfigRepository.save(config);
        } else {
            DailyConfig config = DailyConfig.builder()
                    .id(Snowflake.nextId())
                    .user(user)
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
            User u = userService.getUserById(userId);
            if (u != null) {
                long limit = getUserTokenLimit(u);
                if (usage >= limit) {
                    overuseCount++;
                }
            }
        }
        return overuseCount;
    }

    @Override
    public Long getDailyTokenLimit() {
        return getGlobalQuota();
    }

    @Override
    public void setDailyTokenLimit(Long value) {
        setGlobalQuota(value);
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
            String modelKey = String.valueOf(summary.getModel().getId());
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

    @Transactional
    @Override
    public void aggregateDailyData() {
        LocalDate today = LocalDate.now();
        List<Object[]> modelData = tokenUsageLogRepository.sumByModelIdForDate(today);

        for (Object[] row : modelData) {
            Long modelId = (Long) row[0];
            Long totalTokens = (Long) row[1];

            Optional<TokenDailySummary> existingSummary = tokenDailySummaryRepository.findByUsageDateAndModel_Id(today, modelId);
            Optional<Models> model = modelsRepository.findById(modelId);
            if (existingSummary.isPresent()) {
                TokenDailySummary summary = existingSummary.get();
                summary.setTotalTokens(totalTokens);
                tokenDailySummaryRepository.save(summary);
            } else if (model.isPresent()) {
                TokenDailySummary summary = TokenDailySummary.builder()
                        .usageDate(today)
                        .model(model.get())
                        .totalTokens(totalTokens)
                        .build();
                tokenDailySummaryRepository.save(summary);
            } else {
                log.error("Model was notfound by id: {}", modelId);
            }
        }

        log.info("Daily token usage aggregated for date: {}", today);
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
                long limit = getUserTokenLimit(user);
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

    // ======================================================================
    // 兼容对方版本：配额管理
    // ======================================================================

    @Override
    public long getGlobalQuota() {
        return globalConfigRepository.findById(GLOBAL_QUOTA_KEY)
                .map(GlobalConfig::getValue)
                .map(value -> parseQuota(value, DEFAULT_QUOTA))
                .orElse(DEFAULT_QUOTA);
    }

    @Override
    public long getUserQuota(Long userId) {
        if (userId == null) {
            return getGlobalQuota();
        }

        String cacheLimit = redisTemplate.opsForValue().get(LIMIT_KEY_PREFIX + userId);
        if (cacheLimit != null) {
            try {
                return Long.parseLong(cacheLimit);
            } catch (NumberFormatException ignored) {
            }
        }

        Optional<DailyConfig> config = dailyConfigRepository.findByUser_Id(userId);
        if (config.isPresent()) {
            return config.get().getDailyLimit();
        }

        return getGlobalQuota();
    }

    @Override
    public void setGlobalQuota(long quota) {
        validateQuota(quota);
        globalConfigRepository.save(new GlobalConfig(
                GLOBAL_QUOTA_KEY,
                String.valueOf(quota)
        ));
    }

    @Override
    public void setUserQuota(Long userId, long quota) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }
        validateQuota(quota);

        globalConfigRepository.save(new GlobalConfig(
                USER_QUOTA_KEY_PREFIX + userId,
                String.valueOf(quota)
        ));

        redisTemplate.opsForValue().set(LIMIT_KEY_PREFIX + userId, String.valueOf(quota));

        Optional<DailyConfig> existingConfig = dailyConfigRepository.findByUser_Id(userId);
        if (existingConfig.isPresent()) {
            DailyConfig c = existingConfig.get();
            c.setDailyLimit(quota);
            dailyConfigRepository.save(c);
        } else {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                DailyConfig config = DailyConfig.builder()
                        .id(Snowflake.nextId())
                        .user(user)
                        .dailyLimit(quota)
                        .build();
                dailyConfigRepository.save(config);
            }
        }
    }

    @Override
    public DepartmentUsageData getDepartmentUsage() {
        LocalDate today = LocalDate.now();
        Map<Long, Long> usageByUser = sumUsageByUserFromLogs(
                tokenUsageLogRepository.findByUsageDate(today)
        );

        Map<String, long[]> departmentAggregates = new LinkedHashMap<>();
        List<DepartmentUsageData.UserUsageRow> userRows = new ArrayList<>();

        for (User user : getActiveUsers()) {
            long used = usageByUser.getOrDefault(user.getId(), 0L);
            long quota = getUserQuota(user.getId());
            boolean overQuota = used >= quota;
            String department = normalizeDepartment(user.getDepartment());

            userRows.add(new DepartmentUsageData.UserUsageRow(
                    user.getId(),
                    user.getName(),
                    department,
                    used,
                    quota,
                    overQuota
            ));

            long[] aggregate = departmentAggregates.computeIfAbsent(
                    department,
                    ignored -> new long[3]
            );
            aggregate[0]++;
            aggregate[1] += used;
            if (overQuota) {
                aggregate[2]++;
            }
        }

        List<DepartmentUsageData.DepartmentRow> departmentRows =
                departmentAggregates.entrySet().stream()
                        .map(entry -> new DepartmentUsageData.DepartmentRow(
                                entry.getKey(),
                                entry.getValue()[0],
                                entry.getValue()[1],
                                entry.getValue()[2]
                        ))
                        .sorted(Comparator.comparing(DepartmentUsageData.DepartmentRow::department))
                        .toList();

        userRows.sort(
                Comparator.comparing(DepartmentUsageData.UserUsageRow::department)
                        .thenComparing(
                                DepartmentUsageData.UserUsageRow::totalTokens,
                                Comparator.reverseOrder()
                        )
        );

        return new DepartmentUsageData(
                today.toString(),
                departmentRows,
                userRows
        );
    }

    @Override
    public Map<String, Object> getUserWeeklyTrend(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);

        Map<LocalDate, Long> usageByDate = tokenUsageLogRepository
                .findByUser_IdAndUsageDateBetween(userId, start, today)
                .stream()
                .collect(Collectors.groupingBy(
                        TokenUsageLog::getUsageDate,
                        Collectors.summingLong(TokenUsageLog::getTokensUsed)
                ));

        String[] weekdays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        long total = 0;
        int activeDays = 0;

        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            labels.add(String.format(
                    "%02d-%02d %s",
                    date.getMonthValue(),
                    date.getDayOfMonth(),
                    weekdays[date.getDayOfWeek().getValue() % 7]
            ));

            long value = usageByDate.getOrDefault(date, 0L);
            data.add(value);
            total += value;
            if (value > 0) {
                activeDays++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", labels);
        result.put("data", data);
        result.put("monthlyTotal", total);
        result.put("averageDaily", activeDays == 0 ? 0 : total / activeDays);
        result.put("activeDays", activeDays);
        return result;
    }

    @Override
    public List<Map<String, Object>> getRecentConversations(Long userId) {
        LocalDate today = LocalDate.now();
        return tokenUsageLogRepository
                .findByUser_IdAndUsageDateBetween(userId, today.minusDays(3), today)
                .stream()
                .sorted(Comparator.comparing(TokenUsageLog::getCreatedAt, Comparator.reverseOrder()))
                .limit(20)
                .map(this::toConversationRow)
                .toList();
    }

    @Override
    public Map<String, Object> getDashboardForFrontend() {
        LocalDate today = LocalDate.now();
        long globalQuota = getGlobalQuota();
        Map<Long, Long> usageByUser = sumUsageByUserFromLogs(
                tokenUsageLogRepository.findByUsageDate(today)
        );

        long overQuotaUsers = usageByUser.entrySet().stream()
                .filter(entry -> entry.getValue() >= getUserQuota(entry.getKey()))
                .count();
        long todayTotal = usageByUser.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeUsers", usageByUser.size());
        result.put("overQuotaUsers", overQuotaUsers);
        result.put("todayTotalConsumption", todayTotal);
        result.put("chartData", buildFrontendChart(today));
        result.put("employees", buildEmployeeListForFrontend(usageByUser));
        result.put("globalLimit", globalQuota);
        return result;
    }

    @Override
    public long countActiveUsers() {
        return getActiveUsers().size();
    }

    @Override
    public List<TokenUsageLog> findByUserIdAndUsageDateBetween(
            Long userId,
            LocalDate start,
            LocalDate end
    ) {
        return tokenUsageLogRepository.findByUser_IdAndUsageDateBetween(userId, start, end);
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void dailyReset() {
        tokenUsageLogRepository.deleteByUsageDateBefore(LocalDate.now().minusDays(30));
    }

    private long getCurrentTokenUsage(Long userId) {
        String today = getTodayDateString();
        String usageKey = getUsageKey(today, userId);
        String usageStr = redisTemplate.opsForValue().get(usageKey);

        if (usageStr == null) {
            return sumTokensForUserOnDate(userId, LocalDate.now());
        }

        try {
            return Long.parseLong(usageStr);
        } catch (NumberFormatException e) {
            log.error("Invalid token usage format for user: {}", userId);
            return 0L;
        }
    }

    private long sumTokensForUserOnDate(Long userId, LocalDate date) {
        return tokenUsageLogRepository
                .findByUser_IdAndUsageDate(userId, date)
                .stream()
                .mapToLong(TokenUsageLog::getTokensUsed)
                .sum();
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
        String cacheKey = MODEL_NAME_KEY_PREFIX + modelId;
        String cachedName = redisTemplate.opsForValue().get(cacheKey);
        if (cachedName != null) {
            return cachedName;
        }
        // 缓存不存在，走数据库查询并更新 Redis
        Optional<Models> modelOpt = modelsRepository.findById(modelId);
        if (modelOpt.isPresent()) {
            String modelName = modelOpt.get().getModelName();
            redisTemplate.opsForValue().set(cacheKey, modelName);
            redisTemplate.expireAt(cacheKey, ExpirationTimeHelper.getNextDayTime());
            return modelName;
        }
        return "Model-" + modelId;
    }

    private Map<Long, Long> sumUsageByUserFromLogs(List<TokenUsageLog> logs) {
        return logs.stream()
                .collect(Collectors.groupingBy(
                        log -> log.getUser().getId(),
                        Collectors.summingLong(TokenUsageLog::getTokensUsed)
                ));
    }

    private Map<String, Object> buildFrontendChart(LocalDate today) {
        LocalDate start = today.minusDays(6);
        String[] weekdays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        List<String> labels = new ArrayList<>();
        for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
            LocalDate date = today.minusDays(daysAgo);
            labels.add(String.format(
                    "%02d-%02d %s",
                    date.getMonthValue(),
                    date.getDayOfMonth(),
                    weekdays[date.getDayOfWeek().getValue() % 7]
            ));
        }

        Map<String, Map<LocalDate, Long>> usageByModelAndDate = new LinkedHashMap<>();
        for (TokenUsageLogRepository.ModelDailyTotal total
                : tokenUsageLogRepository.findDailyTotalsSince(start)) {
            usageByModelAndDate
                    .computeIfAbsent(total.getModel(), ignored -> new LinkedHashMap<>())
                    .put(total.getUsageDate(), total.getTotal() == null ? 0L : total.getTotal());
        }

        List<String> modelNames = new ArrayList<>(List.of("千问", "DeepSeek", "Llama-3.1"));
        usageByModelAndDate.keySet().stream()
                .filter(model -> !modelNames.contains(model))
                .sorted()
                .forEach(modelNames::add);

        List<Map<String, Object>> datasets = new ArrayList<>();
        for (String model : modelNames) {
            Map<LocalDate, Long> usageByDate = usageByModelAndDate.getOrDefault(model, Map.of());
            List<Long> data = new ArrayList<>();
            for (int daysAgo = 6; daysAgo >= 0; daysAgo--) {
                data.add(usageByDate.getOrDefault(today.minusDays(daysAgo), 0L));
            }

            Map<String, Object> dataset = new LinkedHashMap<>();
            dataset.put("label", model);
            dataset.put("data", data);
            datasets.add(dataset);
        }

        Map<String, Object> chartData = new LinkedHashMap<>();
        chartData.put("labels", labels);
        chartData.put("datasets", datasets);
        return chartData;
    }

    private List<Map<String, Object>> buildEmployeeListForFrontend(Map<Long, Long> usageByUser) {
        return getActiveUsers().stream()
                .sorted(Comparator.comparing(User::getId).reversed())
                .map(user -> {
                    long used = usageByUser.getOrDefault(user.getId(), 0L);
                    long quota = getUserQuota(user.getId());

                    Map<String, Object> employee = new LinkedHashMap<>();
                    employee.put("id", String.valueOf(user.getId()));
                    employee.put("name", user.getName());
                    employee.put("department", normalizeDepartment(user.getDepartment()));
                    employee.put("totalTokens", used);
                    employee.put("quota", quota);
                    employee.put("overQuota", used >= quota);
                    return employee;
                })
                .toList();
    }

    private Map<String, Object> toConversationRow(TokenUsageLog usageLog) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", usageLog.getId());
        row.put("modelName", usageLog.getModel() != null ? usageLog.getModel().getModelName() : "Unknown");
        row.put("tokensUsed", usageLog.getTokensUsed());
        row.put("usageDate", usageLog.getUsageDate().toString());
        row.put("content", "对话记录");
        row.put("createdAt", usageLog.getCreatedAt() != null ? usageLog.getCreatedAt().toString() : usageLog.getUsageDate().toString());
        return row;
    }

    private List<User> getActiveUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getStatus() != UserStatus.CANCELLED)
                .toList();
    }

    private String normalizeDepartment(String department) {
        return department == null || department.isBlank() ? "未分配" : department;
    }

    private long parseQuota(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private void validateQuota(long quota) {
        if (quota < 0) {
            throw new IllegalArgumentException("Token quota must not be negative");
        }
    }
}
