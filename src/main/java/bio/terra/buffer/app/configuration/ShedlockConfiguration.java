package bio.terra.buffer.app.configuration;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configures shedlock for scheduling background jobs within a multi-instance environment. This
 * allows us to ensure that scheduled tasks are executed at most once at the same time.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT4H")
public class ShedlockConfiguration {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ShedlockConfiguration(BufferDatabaseConfiguration jdbcConfiguration) {
        this.jdbcTemplate = new JdbcTemplate(jdbcConfiguration.getDataSource());
    }

    @Bean
    public LockProvider lockProvider() {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(jdbcTemplate)
                        .usingDbTime()
                        .build());
    }
}
