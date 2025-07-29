package bio.terra.buffer.app.controller.converters;

import bio.terra.buffer.generated.model.SqlSortDirectionDescDefault;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class SqlSortDirectionDescDefaultConverter
    extends OpenApiEnumConverter<SqlSortDirectionDescDefault> {

  @Override
  SqlSortDirectionDescDefault fromValue(String source) {
    return SqlSortDirectionDescDefault.fromValue(source);
  }

  @Override
  String errorString() {
    return String.format(
        "direction must be one of: %s.", Arrays.toString(SqlSortDirectionDescDefault.values()));
  }
}
