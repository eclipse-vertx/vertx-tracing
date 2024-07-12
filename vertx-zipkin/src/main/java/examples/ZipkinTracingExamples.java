package examples;

import brave.Tracing;
import io.vertx.core.Vertx;
import io.vertx.core.VertxBuilder;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.docgen.Source;
import io.vertx.tracing.zipkin.HttpSenderOptions;
import io.vertx.tracing.zipkin.ZipkinTracerFactory;
import io.vertx.tracing.zipkin.ZipkinTracingOptions;

@Source
public class ZipkinTracingExamples {

  public void ex1() {
    Vertx vertx = Vertx.vertx(new VertxOptions()
      .setTracingOptions(
        new ZipkinTracingOptions().setServiceName("A cute service")
      )
    );
  }

  public void ex2(String senderEndpoint) {
    Vertx vertx = Vertx.vertx(new VertxOptions()
      .setTracingOptions(
        new ZipkinTracingOptions()
          .setSenderOptions(new HttpSenderOptions().setSenderEndpoint(senderEndpoint))
      )
    );
  }

  public void ex3(String senderEndpoint, KeyCertOptions sslOptions) {
    Vertx vertx = Vertx.vertx(new VertxOptions()
      .setTracingOptions(
        new ZipkinTracingOptions()
          .setSenderOptions(new HttpSenderOptions()
            .setSenderEndpoint(senderEndpoint)
            .setSsl(true)
            .setKeyCertOptions(sslOptions))
      )
    );
  }

  public void ex4(Tracing tracing) {
    Vertx vertx = Vertx.builder()
      .withTracer(new ZipkinTracerFactory(tracing))
      .build();
  }

  public void ex5(Vertx vertx) {
    HttpServer server = vertx.createHttpServer(new HttpServerOptions()
      .setTracingPolicy(TracingPolicy.IGNORE)
    );
  }

  public void ex6(Vertx vertx) {
    HttpClient client = vertx.createHttpClient(new HttpClientOptions()
      .setTracingPolicy(TracingPolicy.IGNORE)
    );
  }

  public void ex7(Vertx vertx) {
    DeliveryOptions options = new DeliveryOptions().setTracingPolicy(TracingPolicy.ALWAYS);
    vertx.eventBus().send("the-address", "foo", options);
  }
}
