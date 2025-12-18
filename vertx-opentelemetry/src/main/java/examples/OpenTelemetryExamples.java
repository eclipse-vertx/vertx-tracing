package examples;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.docgen.Source;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import io.vertx.tracing.opentelemetry.OpenTelemetryTracingFactory;

@Source
public class OpenTelemetryExamples {

  public void ex1() {
    Vertx vertx = Vertx.vertx(new VertxOptions()
      .setTracingOptions(
        new OpenTelemetryOptions()
      )
    );
  }

  public void ex2(OpenTelemetry openTelemetry) {
    Vertx vertx = Vertx
      .builder()
      .withTracer(new OpenTelemetryTracingFactory(openTelemetry))
      .build();
  }

  public void ex3(Vertx vertx) {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
      .setTracingPolicy(TracingPolicy.IGNORE)
    );
  }

  public void ex4(Vertx vertx) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setTracingPolicy(TracingPolicy.IGNORE)
    );
  }

//  public void ex5(Tracer tracer) {
//    Span span = tracer.spanBuilder("my-operation")
//      .setAttribute("some-key", "some-value")
//      .startSpan();
//    OpenTelemetryUtil.setSpan(span);
//    // Do something, e.g. client request
//    span.end();
//  }

  public void ex6(Vertx vertx) {
    DeliveryOptions options = new DeliveryOptions().setTracingPolicy(TracingPolicy.ALWAYS);
    vertx.eventBus().send("the-address", "foo", options);
  }

  public void ex7() {
    SdkTracerProvider sdkTracerProvider = SdkTracerProvider.builder().build();
    OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
      .setTracerProvider(sdkTracerProvider)
      .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
      .buildAndRegisterGlobal();

    Vertx vertx = Vertx
      .builder()
      .withTracer(new OpenTelemetryTracingFactory(openTelemetry))
      .build();
  }
}
