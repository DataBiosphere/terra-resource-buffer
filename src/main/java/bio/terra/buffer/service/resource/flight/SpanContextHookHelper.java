package bio.terra.buffer.service.resource.flight;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StairwayHook;
import io.opencensus.common.Scope;
import io.opencensus.trace.Span;
import io.opencensus.trace.SpanContext;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;

/**
 * A {@link StairwayHook} helper to to start & stop a span in a flight context. This can help all
 * steps in the flight are under the same parent span. This assumes a {@link SpanContext} is already
 * serialized into Flight input map using {@code SPAN_CONTEXT} as map key.
 */
public class SpanContextHookHelper {
  private static final Tracer tracer = Tracing.getTracer();

  /** The flight input map key of {@link SpanContext} . */
  public static final String SPAN_CONTEXT = "spanContext";

  /** Serialize {@link SpanContext} into Stairway's {@link FlightMap}. */
  public static void serializeSpanContext(FlightMap flightMap, SpanContext spanContext) {
    flightMap.put(SPAN_CONTEXT, spanContext);
  }

  /** Start a span using the {@link SpanContext} serialized when submit the flight. */
  public static void startSpan(FlightContext flightContext) {
    SpanContext spanContext =
        flightContext.getInputParameters().get(SPAN_CONTEXT, SpanContext.class);
    System.out.println("~~~~~~1111111 spanContext");
    System.out.println(spanContext.getSpanId());
    System.out.println(spanContext.getTraceId());
    System.out.println(spanContext.getTracestate());
    // The children span name would be flight name plus step index, e.g.
    // CreateGoogleProjectStep.step1.
    String spanName = flightContext.getFlightClassName() + ".step" + flightContext.getStepIndex();
    System.out.println(spanName);
    Span span = tracer.spanBuilderWithRemoteParent(spanName, spanContext).startSpan();
    Scope ws = tracer.withSpan(span);
  }

  /** End the current span. This assumes all children span are closed correctly. */
  public static void endSpan() {
    System.out.println("~~~~~~222222 endSpan");
    System.out.println(tracer.getCurrentSpan().getContext());
    tracer.getCurrentSpan().end();
  }
}
