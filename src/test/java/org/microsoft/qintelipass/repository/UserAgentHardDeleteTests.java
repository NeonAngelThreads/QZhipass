package org.microsoft.qintelipass.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.microsoft.qintelipass.models.UserAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@Import(UserAgentHardDeleteTests.TestSecurityConfiguration.class)
class UserAgentHardDeleteTests {
    @Autowired private UserAgentRepository repository;
    @PersistenceContext private EntityManager entityManager;

    @Test
    void hardDeleteRemovesThePhysicalDatabaseRow() {
        UserAgent saved = repository.saveAndFlush(UserAgent.builder()
                .userId(1001L)
                .name("硬删除测试")
                .prompt("测试提示词")
                .status(UserAgent.STATUS_ACTIVE)
                .build());

        assertThat(repository.hardDeleteByIdAndUserId(saved.getId(), 1001L)).isEqualTo(1);
        entityManager.clear();
        assertThat(repository.findById(saved.getId())).isEmpty();
        Number rowCount = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM user_agents WHERE id = ?")
                .setParameter(1, saved.getId())
                .getSingleResult();
        assertThat(rowCount.longValue()).isZero();
    }

    @TestConfiguration
    static class TestSecurityConfiguration {
        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
