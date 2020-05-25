package examples;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.docgen.Source;
import io.vertx.tracing.opentracing.OpenTracingOptions;

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
}
