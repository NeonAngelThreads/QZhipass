package org.microsoft.qintelipass;

import java.util.List;

public interface ITrafficStatService {
    void recordTraffic(Long userId);
    void resetTraffic(Long userId);
    List<Long> getAllActiveUsers();
    Integer getActiveUsers();
}
