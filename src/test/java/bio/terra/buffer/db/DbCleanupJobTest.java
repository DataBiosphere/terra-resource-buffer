package bio.terra.buffer.db;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import bio.terra.buffer.app.configuration.DbCleanupJobConfiguration;
import bio.terra.buffer.common.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DbCleanupJobTest extends BaseUnitTest {

  private BufferDao bufferDao;
  private DbCleanupJobConfiguration dbCleanupJobConfiguration;
  private DbCleanupJob dbCleanupJob;

  @BeforeEach
  void setup() {
    bufferDao = mock(BufferDao.class);
    dbCleanupJobConfiguration = new DbCleanupJobConfiguration();
    dbCleanupJob = new DbCleanupJob(bufferDao, dbCleanupJobConfiguration);
  }

  @Test
  void testTableCleanupJob() {
    dbCleanupJobConfiguration.setEnabled(true);
    dbCleanupJobConfiguration.setRetentionDays(10);
    dbCleanupJobConfiguration.setBatchSize(100);

    dbCleanupJob.tableCleanupJob();

    verify(bufferDao)
        .removeDeadCleanupRecords(
            dbCleanupJobConfiguration.getRetentionDays(), dbCleanupJobConfiguration.getBatchSize());
    verify(bufferDao)
        .removeDeadResourceRecords(
            dbCleanupJobConfiguration.getRetentionDays(), dbCleanupJobConfiguration.getBatchSize());
  }

  @Test
  void testTableCleanupJob_doesNothingWhenDisabled() {
    dbCleanupJobConfiguration.setEnabled(false);
    dbCleanupJobConfiguration.setRetentionDays(10);
    dbCleanupJobConfiguration.setBatchSize(100);

    dbCleanupJob.tableCleanupJob();

    verify(bufferDao, never()).removeDeadCleanupRecords(anyInt(), anyInt());
    verify(bufferDao, never()).removeDeadResourceRecords(anyInt(), anyInt());
  }

  @Test
  void testTableCleanupJob_doesNothingWithNoRetentionDays() {
    dbCleanupJobConfiguration.setEnabled(true);
    dbCleanupJobConfiguration.setRetentionDays(0);
    dbCleanupJobConfiguration.setBatchSize(100);

    dbCleanupJob.tableCleanupJob();

    verify(bufferDao, never()).removeDeadCleanupRecords(anyInt(), anyInt());
    verify(bufferDao, never()).removeDeadResourceRecords(anyInt(), anyInt());
  }

  @Test
  void testTableCleanupJob_doesNothingWithNoBatchSize() {
    dbCleanupJobConfiguration.setEnabled(true);
    dbCleanupJobConfiguration.setRetentionDays(10);
    dbCleanupJobConfiguration.setBatchSize(0);

    dbCleanupJob.tableCleanupJob();

    verify(bufferDao, never()).removeDeadCleanupRecords(anyInt(), anyInt());
    verify(bufferDao, never()).removeDeadResourceRecords(anyInt(), anyInt());
  }
}
