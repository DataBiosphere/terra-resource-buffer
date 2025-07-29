package bio.terra.buffer.app.controller.converters;


import bio.terra.buffer.generated.model.SqlSortDirectionAscDefault;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class SqlSortDirectionAscDefaultConverter
    extends OpenApiEnumConverter<SqlSortDirectionAscDefault> {

  @Override
  SqlSortDirectionAscDefault fromValue(String source) {
    return SqlSortDirectionAscDefault.fromValue(source);
  }

  @Override
  String errorString() {
    return String.format(
        "direction must be one of: %s.", Arrays.toString(SqlSortDirectionAscDefault.values()));
  }
}
