package io.vertx.tracing.zipkin;

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;

public class ZipkinTracingOptions extends TracingOptions {

  private HttpSenderOptions senderOptions = new HttpSenderOptions();
  private HttpTracing httpTracing;

  public ZipkinTracingOptions(HttpTracing httpTracing) {
    this.httpTracing = httpTracing;
  }

  public ZipkinTracingOptions(Tracing tracing) {
    this.httpTracing = HttpTracing.newBuilder(tracing).build();
  }

  public ZipkinTracingOptions() {
  }

  public ZipkinTracingOptions(JsonObject json) {
    super(json);
  }


  public HttpClientOptions getSenderOptions() {
    return senderOptions;
  }

  public ZipkinTracingOptions setSenderOptions(HttpSenderOptions senderOptions) {
    this.senderOptions = senderOptions;
    return this;
  }

  VertxTracer<?, ?> buildTracer() {
    if (httpTracing != null) {
      return new ZipkinTracer(false, httpTracing);
    } else if (senderOptions != null) {
      Sender sender = new VertxSender(senderOptions);
      Tracing tracing = Tracing
        .newBuilder()
        .localServiceName("the_service")
        .spanReporter(AsyncReporter.builder(sender).build())
        .sampler(Sampler.ALWAYS_SAMPLE)
        .build();
      return new ZipkinTracer(true, tracing);
    } else {
      return null;
    }
  }
}
