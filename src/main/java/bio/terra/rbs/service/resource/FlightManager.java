package bio.terra.rbs.service.resource;

import bio.terra.rbs.db.Pool;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.db.Resource;
import bio.terra.stairway.Stairway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Manages the Stairway flights to create or delete resources. */
@Component
public class FlightManager {
  private Logger logger = LoggerFactory.getLogger(FlightManager.class);

  private static
  private final Stairway stairway;
  private final RbsDao rbsDao;
  private final TransactionTemplate transactionTemplate;

  public FlightManager(Stairway stairway, RbsDao rbsDao, TransactionTemplate transactionTemplate) {
    this.stairway = stairway;
    this.rbsDao = rbsDao;
    this.transactionTemplate = transactionTemplate;
  }

  public void submitCreationFlight(Pool pool) {
    // TODO: Implement.
    String flightId = stairway.createFlightId();
    stairway.submitToQueue();
  }

  public void submitDeletionFlight(Resource resource) {
    // TODO: Implement.
  }
}
