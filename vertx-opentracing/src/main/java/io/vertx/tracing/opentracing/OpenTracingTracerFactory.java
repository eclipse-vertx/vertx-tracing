package io.vertx.tracing.opentracing;

import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

public class OpenTracingTracerFactory implements VertxTracerFactory {

  @Override
  public VertxTracer tracer(TracingOptions options) {
    OpenTracingOptions openTracingOptions;
    if (options instanceof OpenTracingOptions) {
      openTracingOptions = (OpenTracingOptions) options;
    } else {
      openTracingOptions = new OpenTracingOptions();
    }
    return openTracingOptions.buildTracer();
  }

  @Override
  public OpenTracingOptions newOptions() {
    return new OpenTracingOptions();
  }

  @Override
  public OpenTracingOptions newOptions(JsonObject jsonObject) {
    return new OpenTracingOptions(jsonObject);
  }
}
