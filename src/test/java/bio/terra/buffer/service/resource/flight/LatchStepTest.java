package bio.terra.buffer.service.resource.flight;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.buffer.common.BaseUnitTest;
import bio.terra.buffer.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Reference:
 * https://github.com/DataBiosphere/terra-resource-janitor/blob/master/src/test/java/bio/terra/janitor/service/cleanup/flight/LatchStepTest.java
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class LatchStepTest extends BaseUnitTest {
  @Autowired StairwayComponent stairwayComponent;

  @Test
  public void latchBlocksUntilReleased() throws Exception {
    String flightId = stairwayComponent.get().createFlightId();
    stairwayComponent.get().submit(flightId, LatchFlight.class, new FlightMap());

    LatchStep.startNewLatch();
    TimeUnit.SECONDS.sleep(2);
    assertTrue(stairwayComponent.get().getFlightState(flightId).isActive());

    LatchStep.releaseLatch();
    TimeUnit.SECONDS.sleep(2);
    assertFalse(stairwayComponent.get().getFlightState(flightId).isActive());
  }

  public static class LatchFlight extends Flight {
    public LatchFlight(FlightMap inputParameters, Object applicationContext) {
      super(inputParameters, applicationContext);
      addStep(new LatchStep());
    }
  }
}
