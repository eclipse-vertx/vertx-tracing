package io.vertx.tests.opentracing.it;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class ServerVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    vertx.createHttpServer().requestHandler(httpServerRequest -> {
      httpServerRequest.response().end("Hello from the Server");
    }).listen(8080).onComplete(httpServerAsyncResult -> {
      if (httpServerAsyncResult.succeeded()) {
        startPromise.complete();
      } else {
        startPromise.fail(httpServerAsyncResult.cause());
      }
    });
  }

}
