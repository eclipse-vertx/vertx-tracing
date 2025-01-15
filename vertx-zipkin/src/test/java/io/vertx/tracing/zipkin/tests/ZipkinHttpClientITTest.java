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
package io.vertx.tracing.zipkin.tests;

import brave.propagation.TraceContext;
import brave.test.http.ITHttpAsyncClient;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.tracing.zipkin.ZipkinTracer;
import io.vertx.tracing.zipkin.ZipkinTracingOptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.OPTIONS;

public class ZipkinHttpClientITTest extends ITHttpAsyncClient<HttpClient> {

  private Vertx vertx;

  @Test
  @Disabled
  @Override
  public void usesParentFromInvocationTime() {
    // Does not pass
  }

  @Test
  @Disabled
  @Override
  public void customSampler() {
    // Does not pass
  }

  @Test
  @Disabled
  @Override
  protected void callbackContextIsFromInvocationTime() {
    // Does not pass
  }

  @Override
  protected HttpClient newClient(int port) {
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new ZipkinTracingOptions(httpTracing)));
    return vertx.createHttpClient(new HttpClientOptions()
      .setDefaultPort(port)
      .setDefaultHost("127.0.0.1")
      .setTracingPolicy(TracingPolicy.ALWAYS)
    );
  }

  @Override
  protected void closeClient(HttpClient client) {
    if (client != null) {
      client.close().await();
    }
    if (vertx != null) {
      vertx.close().await();
    }
  }

  @Override
  protected void get(HttpClient client, String path, BiConsumer<Integer, Throwable> callback) {
    client.request(GET, path)
      .compose(req -> req.send().map(HttpClientResponse::statusCode))
      .toCompletionStage().whenComplete(callback);
  }

  private Future<Void> request(HttpClient client, String pathIncludingQuery, String body, HttpMethod method) {
    Promise<Void> promise = Promise.promise();
    Runnable task = () -> {
      if (body == null) {
        RequestOptions options = new RequestOptions()
          .setURI(pathIncludingQuery)
          .setMethod(method)
          .setFollowRedirects(true);
        client.request(options)
          .compose(req -> req.send().compose(resp -> resp.body().<Void>mapEmpty()))
          .onComplete(promise);
      } else {
        client.request(HttpMethod.POST, pathIncludingQuery)
          .compose(req -> req.send(Buffer.buffer(body)).compose(resp -> resp.body().<Void>mapEmpty()))
          .onComplete(promise);
      }
    };
    TraceContext traceCtx = currentTraceContext.get();
    if (traceCtx != null) {
      vertx.runOnContext(v -> {
        ZipkinTracer.setTraceContext(traceCtx);
        task.run();
      });
    } else {
      task.run();
    }
    return promise.future();
  }

  @Override
  protected void get(HttpClient client, String pathIncludingQuery) throws IOException {
    try {
      request(client, pathIncludingQuery, null, GET).await(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new IOException(e);
    }
  }

  @Override
  protected void post(HttpClient client, String pathIncludingQuery, String body) throws IOException {
    try {
      request(client, pathIncludingQuery, body, null).await(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new IOException(e);
    }
  }

  @Override
  protected void options(HttpClient client, String path) throws IOException {
    try {
      request(client, path, null, OPTIONS).await(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new IOException(e);
    }
  }
}
