/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */package io.vertx.tracing.opentracing;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class HttpTest {

  private MockTracer tracer;
  private Vertx vertx;

  @Before
  public void before() {
    tracer = new MockTracer();
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new OpenTracingOptions(tracer)));
  }

  @After
  public void after(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void testClientHttpResponseFailure(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async();
    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.IGNORE))
      .requestHandler(req -> {
        req.connection().close();
    }).listen(8080, "localhost")
      .onComplete(ctx.asyncAssertSuccess(v -> listenLatch.complete()));
    listenLatch.awaitSuccess(20_000);
    Async closedLatch = ctx.async();
    HttpClient client;
    client = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(8080).setTracingPolicy(TracingPolicy.ALWAYS));
    client.request(new RequestOptions().setPort(8080).setHost("localhost"), ctx.asyncAssertSuccess(req -> {
      req.send();
      req.connection().closeHandler(v -> closedLatch.complete());
    }));
    closedLatch.awaitSuccess(20_000);
    long now = System.currentTimeMillis();
    while (tracer.finishedSpans().size() < 1) {
      ctx.assertTrue(System.currentTimeMillis() - now < 20_000);
      Thread.sleep(10);
    }
    List<MockSpan> spans = tracer.finishedSpans();
    assertEquals(1, spans.size());
    MockSpan span = spans.get(0);
    ctx.assertEquals("GET", span.operationName());
    Map<String, Object> tags = span.tags();
    assertEquals("http://localhost:8080/", tags.get("http.url"));
    assertEquals(true, tags.get("error"));
    assertEquals("GET", tags.get("http.method"));
    assertEquals("client", tags.get("span.kind"));
  }
}
