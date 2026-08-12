/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.opentracing;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.tracing.opentracing.OpenTracingTracerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tasks scheduled from a handler keep running on the request or message scoped Vert.x context after the
 * server or consumer span has finished: spans they create must remain part of the trace.
 */
@RunWith(VertxUnitRunner.class)
public class AsyncTaskTracingTest {

  private static final String ADDRESS = "the-address";

  private MockTracer tracer;
  private Vertx vertx;
  private HttpClient client;
  private HttpServer server;

  @Before
  public void before() throws Exception {
    tracer = new MockTracer();
    vertx = Vertx.builder().withTracer(new OpenTracingTracerFactory(tracer)).build();
    server = vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.IGNORE))
      .requestHandler(req -> req.response().end())
      .listen(0)
      .await(20, TimeUnit.SECONDS);
    client = vertx.createHttpClient();
  }

  @After
  public void after(TestContext context) {
    client.close();
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testClientCallAfterPublishConsumerHandlerReturnsJoinsTheTrace(TestContext ctx) throws Exception {
    vertx.eventBus().consumer(ADDRESS, msg ->
      // runs after the handler has returned and the consumer span has finished
      vertx.runOnContext(v ->
        client.request(new RequestOptions().setMethod(HttpMethod.GET).setHost("localhost").setPort(server.actualPort()))
          .compose(HttpClientRequest::send)
          .compose(HttpClientResponse::body)
      )
    ).completion().await(20, TimeUnit.SECONDS);

    vertx.getOrCreateContext().runOnContext(v ->
      vertx.eventBus().publish(ADDRESS, "ping", new DeliveryOptions().setTracingPolicy(TracingPolicy.ALWAYS))
    );

    long now = System.currentTimeMillis();
    while (true) {
      List<MockSpan> spans = tracer.finishedSpans();
      MockSpan consumerSpan = spans.stream()
        .filter(span -> "publish".equals(span.operationName())
          && Tags.SPAN_KIND_SERVER.equals(span.tags().get(Tags.SPAN_KIND.getKey())))
        .findFirst()
        .orElse(null);
      List<MockSpan> clientSpans = spans.stream()
        .filter(span -> "GET".equals(span.operationName())
          && Tags.SPAN_KIND_CLIENT.equals(span.tags().get(Tags.SPAN_KIND.getKey())))
        .collect(Collectors.toList());
      if (consumerSpan != null && !clientSpans.isEmpty()) {
        for (MockSpan clientSpan : clientSpans) {
          // the default HTTP client policy is PROPAGATE: the span only exists if the trace was propagated
          ctx.assertEquals(consumerSpan.context().traceId(), clientSpan.context().traceId());
        }
        break;
      }
      ctx.assertTrue(System.currentTimeMillis() - now < 10_000L, "Expected a client span joining the consumer trace");
      Thread.sleep(10);
    }
  }
}
