package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.flight.SpanContextHookHelper.endSpan;
import static bio.terra.buffer.service.resource.flight.SpanContextHookHelper.startSpan;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.HookAction;
import bio.terra.stairway.StairwayHook;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

/** A {@link StairwayHook} to add span context for each step. */
public class SpanContextHook implements StairwayHook {
  private static final Tracer tracer = Tracing.getTracer();

  /** The flight input map key of {@link SpanContext} . */
  public static final String SPAN_CONTEXT = "spanContext";

  @Override
  public HookAction startFlight(FlightContext flightContext) throws InterruptedException {
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction startStep(FlightContext flightContext) throws InterruptedException {
    startSpan(flightContext);
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction endStep(FlightContext flightContext) throws InterruptedException {
    endSpan();
    return HookAction.CONTINUE;
  }

  @Override
  public HookAction endFlight(FlightContext flightContext) throws InterruptedException {
    return HookAction.CONTINUE;
  }
}
