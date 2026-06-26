package org.microsoft.qintelipass;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class LoginStrategyFactory {
    private final Map<String, ILoginStrategy> strategyMap;

    public LoginStrategyFactory(Map<String, ILoginStrategy> strategyMap) {
        this.strategyMap = new HashMap<>();
        strategyMap.forEach((beanName, strategy) ->
                this.strategyMap.put(strategy.getType(), strategy)
        );
    }

    public ILoginStrategy getStrategy(String loginType) {
        ILoginStrategy strategy = strategyMap.get(loginType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported Login Type: " + loginType);
        }
        return strategy;
    }
}
