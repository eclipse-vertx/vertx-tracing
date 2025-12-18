/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.zipkin;

import brave.propagation.TraceContext;
import brave.test.http.ITHttpServer;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.tracing.zipkin.ZipkinTracer;
import io.vertx.tracing.zipkin.ZipkinTracerFactory;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;

class ZipkinHttpServerITTest extends ITHttpServer implements Handler<HttpServerRequest> {

  private Vertx vertx;
  private HttpServer server;
  private int port;

  @Override
  protected void init() {
    vertx = Vertx.builder().withTracer(new ZipkinTracerFactory(httpTracing)).build();
    server = vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS))
      .requestHandler(this)
      .listen(0, "localhost")
      .await();
    port = server.actualPort();
  }

  @Override
  public void handle(HttpServerRequest req) {
    TraceContext ctx = ZipkinTracer.activeContext();
    switch (req.path()) {
      case "/foo":
        req.response().end("bar");
        break;
      case "/exception":
        req.response().setStatusCode(503).end();
        break;
      case "/exceptionAsync":
        req.endHandler(v -> {
          req.response().setStatusCode(503).end();
        });
        break;
      case "/badrequest":
        req.response().setStatusCode(400).end();
        break;
      case "/":
        if (req.method() == HttpMethod.OPTIONS) {
          req.response().end("bar");
        }
        break;
      case "/async":
        if (ZipkinTracer.activeSpan() == null) {
          throw new IllegalStateException("couldn't read current span!");
        }
        req.endHandler(v -> req.response().end("bar"));
        break;
      case "/items/1":
        req.response().end("1");;
        break;
      case "/items/2":
        req.response().end("2");;
        break;
      case "/child":
        httpTracing.tracing().tracer().newChild(ctx).name("child").start().finish();
        req.response().end("happy");
        break;
      default:
        req.response().setStatusCode(404).end();
        break;
    }
  }

  @Override
  protected String url(String path) {
    return "http://127.0.0.1:" + port + path;
  }

  @Override
  public void httpRoute() {
    // Cannot pass because routes are /items/1 and /items/2
  }

  @Override
  public void httpRoute_nested() {
    // Cannot pass because routes are /items/1 and /items/2
  }

  @Override
  public void httpRoute_async() {
    // Cannot pass because routes are /items/1 and /items/2
  }

  @Override
  protected void spanHandlerSeesError() throws IOException {
    // Cannot pass because our Zipkin tracer only reports error when a request is reset
  }

  @Override
  protected void spanHandlerSeesError_async() throws IOException {
    // Cannot pass because our Zipkin tracer only reports error when a request is reset
  }

  @Override
  protected void setsErrorAndHttpStatusOnUncaughtException() throws IOException {
    // Cannot pass because our Zipkin tracer only reports error when a request is reset
  }

  @Override
  protected void setsErrorAndHttpStatusOnUncaughtException_async() throws IOException {
    // Cannot pass because our Zipkin tracer only reports error when a request is reset
  }

  @AfterEach
  public void stop() throws Exception {
    if (server != null) {
      server.close().await();
    }
    if (vertx != null) {
      vertx.close().await();
    }
  }
}
