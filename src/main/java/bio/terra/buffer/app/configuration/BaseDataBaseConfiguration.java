package bio.terra.buffer.app.configuration;

import bio.terra.common.db.BaseDatabaseProperties;
import bio.terra.common.db.DataSourceInitializer;
import javax.sql.DataSource;

public abstract class BaseDataBaseConfiguration {
  private final BaseDatabaseProperties databaseProperties;
  private final DataSource dataSource;

  public BaseDataBaseConfiguration(BaseDatabaseProperties databaseProperties) {
    this.databaseProperties = databaseProperties;
    dataSource = DataSourceInitializer.initializeDataSource(databaseProperties);
  }

  // The DataSource is nested inside this configuration, not directly injectable.
  // TODO(PF-594): Make DataSource a bean instead of a variable inside a configuration.
  public DataSource getDataSource() {
    return dataSource;
  }
}
