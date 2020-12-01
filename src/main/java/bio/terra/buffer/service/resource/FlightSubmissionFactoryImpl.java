package bio.terra.buffer.service.resource;

import bio.terra.buffer.common.Pool;
import bio.terra.buffer.common.Resource;
import bio.terra.buffer.common.ResourceType;
import bio.terra.buffer.service.resource.flight.GoogleProjectCreationFlight;
import bio.terra.buffer.service.resource.flight.GoogleProjectDeletionFlight;
import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import com.google.common.collect.ImmutableMap;
import io.opencensus.trace.Span;
import io.opencensus.trace.Tracer;
import io.opencensus.trace.Tracing;
import org.springframework.stereotype.Component;

import static bio.terra.buffer.service.resource.FlightMapKeys.SPAN_CONTEXT;
import static bio.terra.buffer.service.resource.flight.SpanContextHookHelper.SPAN_CONTEXT;
import static bio.terra.buffer.service.resource.flight.SpanContextHookHelper.serializeSpanContext;

@Component
public class FlightSubmissionFactoryImpl implements FlightSubmissionFactory {
  /** Supported resource creation flight map. */
  private static final ImmutableMap<ResourceType, Class<? extends Flight>> CREATION_FLIGHT_MAP =
      ImmutableMap.of(ResourceType.GOOGLE_PROJECT, GoogleProjectCreationFlight.class);

  /** Supported resource deletion flight map. */
  private static final ImmutableMap<ResourceType, Class<? extends Flight>> DELETION_FLIGHT_MAP =
      ImmutableMap.of(ResourceType.GOOGLE_PROJECT, GoogleProjectDeletionFlight.class);

  private static final Tracer tracer = Tracing.getTracer();
  private static final String CREATE_GOOGLE_PROJECT_SPAN_NAME = "google.project.create";
  private static final String DELETE_GOOGLE_PROJECT_SPAN_NAME = "google.project.delete";

  @Override
  public FlightSubmission getCreationFlightSubmission(Pool pool) {
    if (!CREATION_FLIGHT_MAP.containsKey(pool.resourceType())) {
      throw new UnsupportedOperationException(
          String.format(
              "Creation for ResourceType: %s is not supported, PoolId: %s",
              pool.toString(), pool.id()));
    }
    Span span = tracer.spanBuilderWithExplicitParent(CREATE_GOOGLE_PROJECT_SPAN_NAME, null).startSpan();
    try{
      FlightMap flightMap = new FlightMap();
      pool.id().store(flightMap);
      flightMap.put(FlightMapKeys.RESOURCE_CONFIG, pool.resourceConfig());
      serializeSpanContext(flightMap, span.getContext());
      return FlightSubmission.create(CREATION_FLIGHT_MAP.get(pool.resourceType()), flightMap);
    } finally {
      span.end();
    }
  }

  @Override
  public FlightSubmission getDeletionFlightSubmission(Resource resource, ResourceType type) {
    if (!DELETION_FLIGHT_MAP.containsKey(type)) {
      throw new UnsupportedOperationException(
          String.format("Deletion for ResourceType: %s is not supported", type.toString()));
    }
    Span span = tracer.spanBuilderWithExplicitParent(DELETE_GOOGLE_PROJECT_SPAN_NAME, null).startSpan();
    try {
      FlightMap flightMap = new FlightMap();
      resource.id().store(flightMap);
      flightMap.put(FlightMapKeys.CLOUD_RESOURCE_UID, resource.cloudResourceUid());
      serializeSpanContext(flightMap, span.getContext());
      return FlightSubmission.create(DELETION_FLIGHT_MAP.get(type), flightMap);
    } finally {
      span.end();
    }
  }
}
