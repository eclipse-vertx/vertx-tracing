package examples;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.docgen.Source;
import io.vertx.tracing.opentracing.OpenTracingOptions;
import io.vertx.tracing.opentracing.OpenTracingUtil;

@Source
public class OpenTracingExamples {

  public void ex1() {
    Vertx vertx = Vertx.vertx(new VertxOptions()
      .setTracingOptions(
        new OpenTracingOptions()
      )
    );
  }

  public void ex2(Tracer tracer) {
    Vertx vertx = Vertx.vertx(new VertxOptions()
      .setTracingOptions(
        new OpenTracingOptions(tracer)
      )
    );
  }

  public void ex3(Tracer tracer) {
    Span span = tracer.buildSpan("my-operation")
      .withTag("some-key", "some-value")
      .start();
    OpenTracingUtil.setSpan(span);
    // Do something, e.g. client request
    span.finish();
  }
}
