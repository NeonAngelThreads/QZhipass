package org.microsoft.qintelipass.token;

import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Token 用量统计与限额管理服务（独立包，与原注销账户后端分离）
 */
@Service
public class TokenService {

    private static final String QUOTA_KEY = "global_token_quota";
    private static final long DEFAULT_QUOTA = 100_000L;

    @Autowired
    private TokenUsageRepository tokenUsageRepository;

    @Autowired
    private GlobalConfigRepository globalConfigRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 应用启动时，若尚未配置统一限额，则写入默认值
     */
    @PostConstruct
    public void initDefaultQuota() {
        if (globalConfigRepository.findById(QUOTA_KEY).isEmpty()) {
            globalConfigRepository.save(new GlobalConfig(QUOTA_KEY, String.valueOf(DEFAULT_QUOTA)));
        }
    }

    // ===================== 限额管理 =====================

    public long getGlobalQuota() {
        return globalConfigRepository.findById(QUOTA_KEY)
                .map(c -> {
                    try {
                        return Long.parseLong(c.getValue());
                    } catch (NumberFormatException e) {
                        return DEFAULT_QUOTA;
                    }
                })
                .orElse(DEFAULT_QUOTA);
    }

    /** 获取某个用户的 Token 限额（优先使用个人配额，否则使用全局配额） */
    public long getUserQuota(Long userId) {
        if (userId == null) return getGlobalQuota();
        return globalConfigRepository.findById("user_quota_" + userId)
                .map(c -> {
                    try {
                        return Long.parseLong(c.getValue());
                    } catch (NumberFormatException e) {
                        return getGlobalQuota();
                    }
                })
                .orElseGet(this::getGlobalQuota);
    }

    /** 管理员统一设置所有用户的 token 限额（立即生效） */
    public void setGlobalQuota(long quota) {
        if (quota < 0) {
            throw new IllegalArgumentException("Token quota must not be negative");
        }
        globalConfigRepository.save(new GlobalConfig(QUOTA_KEY, String.valueOf(quota)));
    }

    // ===================== 用量记录 =====================

    /**
     * 记录一次对话产生的 token 消耗（按 user + 日期 + 模型 累加）
     */
    @Transactional
    public void recordUsage(Long userId, String model, long promptTokens, long completionTokens) {
        if (userId == null || model == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        TokenUsage tu = tokenUsageRepository
                .findByUserIdAndUsageDateAndModel(userId, today, model)
                .orElse(null);

        if (tu == null) {
            tu = new TokenUsage();
            tu.setUserId(userId);
            tu.setUsageDate(today);
            tu.setModel(model);
        }
        tu.setPromptTokens(tu.getPromptTokens() + promptTokens);
        tu.setCompletionTokens(tu.getCompletionTokens() + completionTokens);
        tu.setTotalTokens(tu.getTotalTokens() + promptTokens + completionTokens);
        tokenUsageRepository.save(tu);
    }

    // ===================== 状态查询 =====================

    /** 获取某用户当日限额与使用状态 */
    public UserTokenStatus getDailyStatus(Long userId) {
        long quota = getUserQuota(userId);
        LocalDate today = LocalDate.now();
        List<TokenUsage> list = tokenUsageRepository.findByUserIdAndUsageDate(userId, today);
        long used = list.stream().mapToLong(TokenUsage::getTotalTokens).sum();
        long remaining = Math.max(0, quota - used);
        return new UserTokenStatus(userId, quota, used, remaining, used >= quota);
    }

    /**
     * 发起对话前检测是否超额
     * @param estimatedTokens 本次对话预计消耗（缺省视为一次普通对话）
     */
    public boolean checkQuota(Long userId, long estimatedTokens) {
        UserTokenStatus status = getDailyStatus(userId);
        return status.used() + estimatedTokens <= status.quota();
    }

    // ===================== 看板数据 =====================

    /** 管理员看板：活跃用户数、超额用户数、分模型一周每日消耗 */
    public DashboardData getDashboard() {
        long quota = getGlobalQuota();
        LocalDate today = LocalDate.now();

        // 今日所有用量，按用户汇总
        List<TokenUsage> todayList = tokenUsageRepository.findByUsageDate(today);
        Map<Long, Long> sumByUser = todayList.stream()
                .collect(Collectors.groupingBy(TokenUsage::getUserId,
                        Collectors.summingLong(TokenUsage::getTotalTokens)));

        long activeUsers = sumByUser.size();
        long overQuotaUsers = sumByUser.values().stream()
                .filter(v -> v >= quota)
                .count();

        // 最近 7 天日期
        List<String> dates = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            dates.add(today.minusDays(i).toString());
        }
        Map<String, List<Long>> models = new HashMap<>();
        List<Long> totals = new ArrayList<>(Collections.nCopies(7, 0L));

        LocalDate start = today.minusDays(6);
        for (TokenUsageRepository.ModelDailyTotal m : tokenUsageRepository.findDailyTotalsSince(start)) {
            models.computeIfAbsent(m.getModel(), k -> new ArrayList<>(Collections.nCopies(7, 0L)));
            int idx = (int) ChronoUnit.DAYS.between(start, m.getUsageDate());
            if (idx >= 0 && idx < 7) {
                models.get(m.getModel()).set(idx, m.getTotal());
                totals.set(idx, totals.get(idx) + m.getTotal());
            }
        }

        return new DashboardData(activeUsers, overQuotaUsers, quota, dates, models, totals);
    }

    /** 管理员视角：按部门统计当日 token 使用情况 + 员工明细 */
    public DepartmentUsageData getDepartmentUsage() {
        long quota = getGlobalQuota();
        LocalDate today = LocalDate.now();

        List<TokenUsage> todayList = tokenUsageRepository.findByUsageDate(today);
        Map<Long, Long> sumByUser = todayList.stream()
                .collect(Collectors.groupingBy(TokenUsage::getUserId,
                        Collectors.summingLong(TokenUsage::getTotalTokens)));

        List<User> users = userRepository.findAll();

        // 部门聚合
        Map<String, long[]> deptAgg = new LinkedHashMap<>();
        List<DepartmentUsageData.UserUsageRow> userRows = new ArrayList<>();

        for (User u : users) {
            long used = sumByUser.getOrDefault(u.getId(), 0L);
            boolean over = used >= quota;
            String dept = u.getDepartment() == null ? "未分配" : u.getDepartment();

            userRows.add(new DepartmentUsageData.UserUsageRow(
                    u.getId(), u.getName(), dept, used, quota, over));

            long[] agg = deptAgg.computeIfAbsent(dept, k -> new long[3]); // [userCount, totalTokens, overQuotaCount]
            agg[0] += 1;
            agg[1] += used;
            if (over) {
                agg[2] += 1;
            }
        }

        List<DepartmentUsageData.DepartmentRow> deptRows = deptAgg.entrySet().stream()
                .map(e -> new DepartmentUsageData.DepartmentRow(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .sorted(Comparator.comparing(DepartmentUsageData.DepartmentRow::department))
                .toList();

        // 员工明细按部门、用量降序
        userRows.sort(Comparator.comparing(DepartmentUsageData.UserUsageRow::department)
                .thenComparing(DepartmentUsageData.UserUsageRow::totalTokens, Comparator.reverseOrder()));

        return new DepartmentUsageData(today.toString(), deptRows, userRows);
    }

    // ===================== 每周趋势（员工视角） =====================

    public Map<String, Object> getUserWeeklyTrend(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);

        List<TokenUsage> usages = tokenUsageRepository.findByUserIdAndUsageDateBetween(userId, start, today);
        Map<LocalDate, Long> dateMap = usages.stream()
                .collect(Collectors.groupingBy(TokenUsage::getUsageDate,
                        Collectors.summingLong(TokenUsage::getTotalTokens)));

        String[] weekdays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        long monthTotal = 0;
        int dayCount = 0;

        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            labels.add(String.format("%02d-%02d %s", d.getMonthValue(), d.getDayOfMonth(), weekdays[d.getDayOfWeek().getValue() % 7]));
            long val = dateMap.getOrDefault(d, 0L);
            data.add(val);
            if (val > 0) { monthTotal += val; dayCount++; }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labels", labels);
        result.put("data", data);
        result.put("monthlyTotal", monthTotal);
        result.put("averageDaily", dayCount > 0 ? monthTotal / dayCount : 0);
        result.put("activeDays", dayCount);
        return result;
    }

    /** 最近对话记录（员工视角） */
    public List<Map<String, Object>> getRecentConversations(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate threeDaysAgo = today.minusDays(3);

        List<TokenUsage> usages = tokenUsageRepository.findByUserIdAndUsageDateBetween(userId, threeDaysAgo, today);
        return usages.stream()
                .sorted(Comparator.comparing(TokenUsage::getUsageDate, Comparator.reverseOrder()))
                .limit(20)
                .map(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("modelName", t.getModel());
                    m.put("tokensUsed", t.getTotalTokens());
                    m.put("usageDate", t.getUsageDate().toString());
                    m.put("content", "对话记录");
                    m.put("createdAt", t.getUsageDate().toString());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ===================== 前端适配：管理员仪表盘 =====================

    public Map<String, Object> getDashboardForFrontend() {
        long quota = getGlobalQuota();
        LocalDate today = LocalDate.now();

        // 今日所有用量，按用户汇总
        List<TokenUsage> todayList = tokenUsageRepository.findByUsageDate(today);
        Map<Long, Long> sumByUser = todayList.stream()
                .collect(Collectors.groupingBy(TokenUsage::getUserId,
                        Collectors.summingLong(TokenUsage::getTotalTokens)));

        long activeUsers = sumByUser.size();
        long overQuotaUsers = sumByUser.values().stream().filter(v -> v >= quota).count();
        long todayTotal = sumByUser.values().stream().mapToLong(Long::longValue).sum();

        // 图表：近7天各模型每日消耗
        List<String> dates = new ArrayList<>();
        String[] weekdays = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            dates.add(String.format("%02d-%02d %s", d.getMonthValue(), d.getDayOfMonth(), weekdays[d.getDayOfWeek().getValue() % 7]));
        }

        LocalDate start = today.minusDays(6);
        List<TokenUsageRepository.ModelDailyTotal> rawTotals = tokenUsageRepository.findDailyTotalsSince(start);
        String[] modelNames = {"千问", "DeepSeek", "Llama-3.1"};
        Map<String, Map<LocalDate, Long>> modelDateMap = new LinkedHashMap<>();
        for (String m : modelNames) modelDateMap.put(m, new LinkedHashMap<>());

        for (TokenUsageRepository.ModelDailyTotal t : rawTotals) {
            modelDateMap.computeIfAbsent(t.getModel(), k -> new LinkedHashMap<>())
                    .put(t.getUsageDate(), t.getTotal());
        }

        List<Map<String, Object>> datasets = new ArrayList<>();
        for (String m : modelNames) {
            Map<String, Object> ds = new LinkedHashMap<>();
            ds.put("label", m);
            Map<LocalDate, Long> dm = modelDateMap.getOrDefault(m, Map.of());
            List<Long> vals = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                vals.add(dm.getOrDefault(today.minusDays(i), 0L));
            }
            ds.put("data", vals);
            datasets.add(ds);
        }

        Map<String, Object> chartData = new LinkedHashMap<>();
        chartData.put("labels", dates);
        chartData.put("datasets", datasets);

        // 员工列表
        List<Map<String, Object>> employees = buildEmployeeListForFrontend(today, sumByUser, quota);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activeUsers", activeUsers);
        result.put("overQuotaUsers", overQuotaUsers);
        result.put("todayTotalConsumption", todayTotal);
        result.put("chartData", chartData);
        result.put("employees", employees);
        result.put("globalLimit", quota);
        return result;
    }

    private List<Map<String, Object>> buildEmployeeListForFrontend(
            LocalDate today, Map<Long, Long> sumByUser, long globalQuota) {
        List<User> allUsers = userRepository.findAll().stream()
                .filter(u -> u.getStatus() == null || !"CANCELLED".equals(u.getStatus().name()))
                .sorted(Comparator.comparing(User::getId).reversed())
                .toList();

        return allUsers.stream().map(u -> {
            Map<String, Object> emp = new LinkedHashMap<>();
            long used = sumByUser.getOrDefault(u.getId(), 0L);
            long limit = getUserQuota(u.getId());  // 优先个人配额
            String status;
            if (used >= limit) status = "over";
            else if (limit > 0 && (double) used / limit > 0.85) status = "warning";
            else status = "normal";

            emp.put("id", String.valueOf(u.getId()));
            emp.put("name", u.getName());
            emp.put("dept", u.getDepartment() != null ? u.getDepartment() : "未分配");
            emp.put("used", used);
            emp.put("limit", limit);
            emp.put("status", status);
            return emp;
        }).collect(Collectors.toList());
    }

    // ===================== 用户配额 =====================

    /** 设定全局配额 */
    public void setGlobalQuotaWithCount(long quota) {
        setGlobalQuota(quota);
    }

    /** 设定单个用户的配额（通过 global_config 存储） */
    public void setUserQuota(Long userId, long quota) {
        if (quota < 0) throw new IllegalArgumentException("Token quota must not be negative");
        globalConfigRepository.save(new GlobalConfig("user_quota_" + userId, String.valueOf(quota)));
    }

    public long countActiveUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getStatus() == null || !"CANCELLED".equals(u.getStatus().name()))
                .count();
    }

    // ===================== 查询用户跨日记录 =====================

    public List<TokenUsage> findByUserIdAndUsageDateBetween(Long userId, LocalDate start, LocalDate end) {
        return tokenUsageRepository.findByUserIdAndUsageDateBetween(userId, start, end);
    }

    // ===================== 种子数据 =====================

    @PostConstruct
    public void seedDemoData() {
        try {
            if (tokenUsageRepository.count() > 0) return;
        } catch (Exception e) { return; }

        LocalDate today = LocalDate.now();
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) return;
        Random random = new Random(42);

        String[] models = {"千问", "DeepSeek", "Llama-3.1"};
        // 用 Set 确保 (user_id, usage_date, model) 唯一
        Set<String> seen = new HashSet<>();

        for (User user : users) {
            if (user.getStatus() != null && "CANCELLED".equals(user.getStatus().name())) continue;
            for (int dayOffset = 6; dayOffset >= 0; dayOffset--) {
                LocalDate date = today.minusDays(dayOffset);
                int recordCount = random.nextInt(3) + 2; // 2-4 per day, with 3 models max
                int created = 0;
                for (int attempt = 0; attempt < recordCount + 2 && created < recordCount; attempt++) {
                    String model = models[random.nextInt(models.length)];
                    String key = user.getId() + "-" + date + "-" + model;
                    if (seen.contains(key)) continue;
                    seen.add(key);

                    long tokens = random.nextInt(50000) + 5000;
                    TokenUsage tu = new TokenUsage();
                    tu.setUserId(user.getId());
                    tu.setUsageDate(date);
                    tu.setModel(model);
                    tu.setPromptTokens(tokens / 2);
                    tu.setCompletionTokens(tokens - tokens / 2);
                    tu.setTotalTokens(tokens);
                    try {
                        tokenUsageRepository.save(tu);
                        created++;
                    } catch (Exception e) { /* skip duplicate */ }
                }
            }
        }
    }

    // ===================== 每日 0 点清零 / 清理 =====================

    /**
     * 每日 0 点执行：清理 30 天前的旧用量记录。
     * 由于用量按日期分表记录，新的一天会自动从 0 开始（自然"清零"）。
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void dailyReset() {
        LocalDate cutoff = LocalDate.now().minusDays(30);
        tokenUsageRepository.deleteByUsageDateBefore(cutoff);
    }
}
