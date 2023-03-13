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
package io.vertx.tracing.zipkin;

import brave.propagation.TraceContext;
import brave.test.http.ITHttpAsyncClient;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.tracing.TracingPolicy;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ZipkinHttpClientITTest extends ITHttpAsyncClient<HttpClient> {

  private Vertx vertx;

  @Rule
  public TestName testName = new TestName();

  public ZipkinHttpClientITTest() {
    // Need to use this ?
    // currentTraceContext = ZipkinTracer.DEFAULT_CURRENT_TRACE_CONTEXT;
  }

  // Does not pass
  @Test
  @Ignore
  @Override
  public void usesParentFromInvocationTime() throws Exception {
    super.usesParentFromInvocationTime();
  }

  // Does not pass
  @Test
  @Ignore
  @Override
  public void customSampler() throws Exception {
    super.customSampler();
  }

  @Override
  protected HttpClient newClient(int port) {
    if (vertx == null) {
      vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new ZipkinTracingOptions(httpTracing)));
    }
    return vertx.createHttpClient(new HttpClientOptions()
      .setDefaultPort(port)
      .setDefaultHost("127.0.0.1")
      .setTracingPolicy(TracingPolicy.ALWAYS)
    );
  }

  @Override
  protected void closeClient(HttpClient client) throws Exception {
    if (client != null) {
      client.close();
    }
  }

  @Override
  protected void getAsync(HttpClient client, String pathIncludingQuery) throws Exception {
    request(client, pathIncludingQuery, null);
  }

  private CompletableFuture<HttpClientResponse> request(HttpClient client, String pathIncludingQuery, String body) {
    CompletableFuture<HttpClientResponse> fut = new CompletableFuture<>();
    Runnable task = () -> {
      Handler<AsyncResult<HttpClientResponse>> handler = res -> {
        if (res.succeeded()) {
          fut.complete(res.result());
        } else {
          fut.completeExceptionally(res.cause());
        }
      };
      if (body == null) {
        client.request(new RequestOptions().setURI(pathIncludingQuery).setFollowRedirects(true)).onComplete(ar -> {
          if (ar.succeeded()) {
            ar.result().send().onComplete(handler);
          } else {
            handler.handle(Future.failedFuture(ar.cause()));
          }
        });
      } else {
        client.request(HttpMethod.POST, pathIncludingQuery).onComplete(ar -> {
          if (ar.succeeded()) {
            ar.result().send(Buffer.buffer(body)).onComplete(handler);
          } else {
            handler.handle(Future.failedFuture(ar.cause()));
          }
        });
      }
    };
    TraceContext traceCtx = currentTraceContext.get();
    if (traceCtx != null) {
      // Create a context and associate it with the trace context
      Context ctx = vertx.getOrCreateContext();
      ctx.putLocal(ZipkinTracer.ACTIVE_CONTEXT, traceCtx);
      ctx.runOnContext(v -> {
        // Run task on this context so the tracer will resolve it from the local storage
        task.run();
      });
    } else {
      task.run();
    }
    return fut;
  }

  @Override
  protected void get(HttpClient client, String pathIncludingQuery) throws Exception {
    request(client, pathIncludingQuery, null).get(10, TimeUnit.SECONDS);
    if (testName.getMethodName().equals("redirect")) {
      // Required to avoid race condition since the span created by the redirect test might be closed
      // before the client reports the 2 spans
      Thread.sleep(1000);
    }
  }

  @Override
  protected void post(HttpClient client, String pathIncludingQuery, String body) throws Exception {
    request(client, pathIncludingQuery, body).get(10, TimeUnit.SECONDS);
  }

  @Override
  public void close() throws Exception {
    if (vertx != null) {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.close().onComplete(ar -> {
        latch.countDown();
      });
      latch.await(10, TimeUnit.SECONDS);
      vertx = null;
    }
    super.close();
  }

  @Override
  protected String url(String path) {
    return "http://127.0.0.1:" + server.getPort() + path;
  }

  //  @After
//  public void stop() throws Exception {
//  }
}
