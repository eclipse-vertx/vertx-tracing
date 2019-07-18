package io.vertx.tracing.zipkin;

import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

public class ZipkinTracerFactory implements VertxTracerFactory {

  @Override
  public VertxTracer tracer(TracingOptions options) {
    ZipkinTracingOptions zipkinOptions;
    if (options instanceof ZipkinTracingOptions) {
      zipkinOptions = (ZipkinTracingOptions) options;
    } else {
      zipkinOptions = new ZipkinTracingOptions();
    }
    return zipkinOptions.buildTracer();
  }

  @Override
  public TracingOptions newOptions() {
    return new ZipkinTracingOptions();
  }

  @Override
  public TracingOptions newOptions(JsonObject jsonObject) {
    return new ZipkinTracingOptions(jsonObject);
  }
}
