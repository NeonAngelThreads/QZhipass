package org.microsoft.qintelipass;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class LoginStrategyFactory {
    private final Map<String, ILoginStrategy> strategyMap;
    private static final Map<String, String> LOGIN_TYPE_ALIASES = Map.of(
            "MOBILE_CODE", "smsLogin",
            "SMS_LOGIN", "smsLogin",
            "smsLogin", "smsLogin"
    );

    public LoginStrategyFactory(Map<String, ILoginStrategy> strategyMap) {
        this.strategyMap = new HashMap<>();
        strategyMap.forEach((beanName, strategy) ->
                this.strategyMap.put(strategy.getType(), strategy)
        );
    }

    public ILoginStrategy getStrategy(String loginType) {
        String normalizedLoginType = LOGIN_TYPE_ALIASES.getOrDefault(loginType, loginType);
        ILoginStrategy strategy = strategyMap.get(normalizedLoginType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported Login Type: " + loginType);
        }
        return strategy;
    }
}
