package io.vertx.tracing.opentelemetry;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.vertx.tracing.opentelemetry.OpenTelemetryUtil.ACTIVE_SPAN;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@ExtendWith(VertxExtension.class)
public class OpenTelemetryUtilTest {
  private static Vertx vertx;


  private static SdkTracerProvider sdkTracerProvider;

  private static Span span;

  private static TestSpanData testSpanData;

  private static final long START_EPOCH_NANOS = TimeUnit.SECONDS.toNanos(3000) + 200;

  private static final long END_EPOCH_NANOS = TimeUnit.SECONDS.toNanos(3001) + 255;

  private static final String FIRST_TRACE_ID = "00000000000000000000000000000061";

  private static final String FIRST_SPAN_ID = "0000000000000061";

  private static final TraceState FIRST_TRACE_STATE =
    TraceState.builder().put("foo", "bar").build();

  @BeforeAll
  public static void setup() {

    sdkTracerProvider = SdkTracerProvider.builder()
      .addSpanProcessor(SimpleSpanProcessor.create(new LoggingSpanExporter()))
      .build();
    span = sdkTracerProvider.tracerBuilder(ACTIVE_SPAN)
      .build()
      .spanBuilder("span")
      .startSpan();
    testSpanData = TestSpanData.builder()
      .setHasEnded(true)
      .setName("spanName")
      .setStartEpochNanos(START_EPOCH_NANOS)
      .setEndEpochNanos(END_EPOCH_NANOS)
      .setKind(SpanKind.SERVER)
      .setSpanContext(SpanContext.create(FIRST_TRACE_ID, FIRST_SPAN_ID, TraceFlags.getDefault(), FIRST_TRACE_STATE))
      .setStatus(StatusData.ok())
      .setTotalRecordedEvents(0)
      .setTotalRecordedLinks(0).build();
    span.setAllAttributes(testSpanData.getAttributes());
    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(sdkTracerProvider)
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .buildAndRegisterGlobal();

    vertx = Vertx.vertx(new VertxOptions()
                          .setTracingOptions(new OpenTelemetryOptions(openTelemetry))
    );

  }

  @AfterAll
  public static void tearDown(VertxTestContext vertxTestContext) {
    vertx.close(vertxTestContext.succeedingThenComplete());
  }

  @Test
  public void getSpan_from_context(VertxTestContext vertxTestContext) {

    vertx.runOnContext(handler -> {

      assertNull(OpenTelemetryUtil.getSpan());
      Context context = Vertx.currentContext();
      context.putLocal(ACTIVE_SPAN, span);
      assertSame(span, OpenTelemetryUtil.getSpan());
      OpenTelemetryUtil.clearContext();
      vertxTestContext.completeNow();
    });
  }

  @Test
  public void setSpan_onContext(VertxTestContext vertxTestContext) {
    vertx.runOnContext(ignored -> {
      assertNull(OpenTelemetryUtil.getSpan());
      OpenTelemetryUtil.setSpan(span);

      Context context = Vertx.currentContext();
      assertSame(span, context.getLocal(ACTIVE_SPAN));
      OpenTelemetryUtil.clearContext();
      vertxTestContext.completeNow();
    });
  }
}
