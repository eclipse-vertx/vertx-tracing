package examples;

import brave.Tracing;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.docgen.Source;
import io.vertx.tracing.zipkin.HttpSenderOptions;
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
    Vertx vertx = Vertx.vertx(new VertxOptions()
      .setTracingOptions(
        new ZipkinTracingOptions(tracing)
      )
    );
  }
}
