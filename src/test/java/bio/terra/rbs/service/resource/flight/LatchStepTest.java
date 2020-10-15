package bio.terra.rbs.service.resource.flight;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.rbs.common.BaseUnitTest;
import bio.terra.rbs.service.stairway.StairwayComponent;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;

@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
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
