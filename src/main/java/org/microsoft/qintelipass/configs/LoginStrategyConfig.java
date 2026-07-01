package org.microsoft.qintelipass.configs;

import org.microsoft.qintelipass.ILoginStrategy;
import org.microsoft.qintelipass.logins.MobileCodeLoginStrategy;
import org.microsoft.qintelipass.logins.PasswordLoginStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LoginStrategyConfig {
    @Bean("smsStrategy")
    public ILoginStrategy smsLoginStrategy() {
        return new MobileCodeLoginStrategy();
    }

    @Bean("passwordStrategy")
    public ILoginStrategy passwordLoginStrategy() {
        return new PasswordLoginStrategy();
    }
}
