package io.vertx.tracing.opentracing.it;

import io.vertx.core.AbstractVerticle;

public class ServerVerticle extends AbstractVerticle {

  @Override
  public void start() {
    vertx.createHttpServer().requestHandler(httpServerRequest -> {
      httpServerRequest.response().end("Hello from the Server");
    }).listen(8080);
  }
}
