package bio.terra.buffer.app.configuration;

import static bio.terra.buffer.app.configuration.BeanNames.BUFFER_DB_DATA_SOURCE;
import static bio.terra.buffer.app.configuration.BeanNames.JDBC_TEMPLATE;
import static bio.terra.buffer.app.configuration.BeanNames.OBJECT_MAPPER;

import bio.terra.buffer.app.StartupInitializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class ApplicationConfiguration {
  @Bean(JDBC_TEMPLATE)
  public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate(
      @Qualifier(BUFFER_DB_DATA_SOURCE) DataSource dataSource) {
    return new NamedParameterJdbcTemplate(dataSource);
  }

  @Bean(OBJECT_MAPPER)
  public ObjectMapper objectMapper() {
    return new ObjectMapper()
        .registerModule(new ParameterNamesModule())
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_ABSENT);
  }

  /**
   * This is a "magic bean": It supplies a method that Spring calls after the application is setup,
   * but before the port is opened for business. That lets us do database migration and stairway
   * initialization on a system that is otherwise fully configured. The rule of thumb is that all
   * bean initialization should avoid database access. If there is additional database work to be
   * done, it should happen inside this method.
   */
  @Bean
  public SmartInitializingSingleton postSetupInitialization(ApplicationContext applicationContext) {
    return () -> {
      StartupInitializer.initialize(applicationContext);
    };
  }

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
