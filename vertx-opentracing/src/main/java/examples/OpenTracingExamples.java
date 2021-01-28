package examples;

import io.opentracing.Tracer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.tracing.TracingPolicy;
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

  public void ex5(Vertx vertx) {
    DeliveryOptions options = new DeliveryOptions().setTracingPolicy(TracingPolicy.ALWAYS);
    vertx.eventBus().send("the-address", "foo", options);
  }
}
