package io.vertx.tracing.opentracing;

import io.opentracing.Tracer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingOptions;

public class OpenTracingOptions extends TracingOptions {

  private Tracer tracer;

  public OpenTracingOptions(Tracer tracer) {
    this.tracer = tracer;
  }

  public OpenTracingOptions() {
  }

  public OpenTracingOptions(JsonObject json) {
    super(json);
  }

  io.vertx.core.spi.tracing.VertxTracer<?, ?> buildTracer() {
    if (tracer != null) {
      return new OpenTracingTracer(false, tracer);
    } else {
      return new OpenTracingTracer(true, OpenTracingTracer.createDefaultTracer());
    }
  }
}
