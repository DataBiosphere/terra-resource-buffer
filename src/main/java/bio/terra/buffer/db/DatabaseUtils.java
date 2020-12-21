package bio.terra.buffer.db;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;

/** Utilities class for database operations. */
public class DatabaseUtils {
  /** Executes a database operation and retries if retryable. */
  public static <T> T executeAndRetry(
      DatabaseExecute<T> execute, Duration retrySleep, int maxNumRetries)
      throws InterruptedException {
    int numRetries = 0;
    while (numRetries < maxNumRetries) {
      try {
        return execute.execute();
      } catch (DataAccessException e) {
        if (!retryQuery(e)) {
          throw e;
        }
      }
      ++numRetries;
      TimeUnit.MILLISECONDS.sleep(retrySleep.toMillis());
    }
    throw new InterruptedException("Exceeds maximum number of retries.");
  }

  /** Returns {@code true} if that is retryable {@link DataAccessException}. */
  public static boolean retryQuery(DataAccessException dataAccessException) {
    return ExceptionUtils.hasCause(dataAccessException, RecoverableDataAccessException.class)
        || ExceptionUtils.hasCause(dataAccessException, TransientDataAccessException.class);
  }

  /** How to execute this database operation. */
  @FunctionalInterface
  public interface DatabaseExecute<T> {
    T execute();
  }
}
