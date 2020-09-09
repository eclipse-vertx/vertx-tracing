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
package io.vertx.tracing.opentracing;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class OpenTracingTest {

  private Vertx vertx;
  private MockTracer tracer;

  @Before
  public void before() {
    tracer = new MockTracer();
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new OpenTracingOptions(tracer)));
  }

  @After
  public void after(TestContext ctx) {
    vertx.close(ctx.asyncAssertSuccess());
  }

  List<MockSpan> waitUntil(int expected) throws Exception {
    long now = System.currentTimeMillis();
    while (tracer.finishedSpans().size() < expected && (System.currentTimeMillis() - now) < 10000 ) {
      Thread.sleep(10);
    }
    assertEquals(expected, tracer.finishedSpans().size());
    return tracer.finishedSpans();
  }

  void assertSingleSpan(List<MockSpan> spans) {
    long result = spans.stream().map(span -> span.context().traceId()).distinct().count();
    assertEquals(1, result);
  }

  @Test
  public void testHttpServerRequest(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async();
    vertx.createHttpServer().requestHandler(req -> {
      req.response().end();
    }).listen(8080, ctx.asyncAssertSuccess(v -> listenLatch.complete()));
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req ->{
      req.send(ctx.asyncAssertSuccess(resp -> {
        responseLatch.complete();
      }));
    }));
    responseLatch.awaitSuccess();
    List<MockSpan> spans = waitUntil(1);
    MockSpan span = spans.get(0);
    assertEquals("GET", span.operationName());
    assertEquals("GET", span.tags().get("http.method"));
    assertEquals("http://localhost:8080/", span.tags().get("http.url"));
    assertEquals("200", span.tags().get("http.status_code"));
  }

  @Test
  public void testHttpClientRequest(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async(2);
    HttpClient c = vertx.createHttpClient();
    vertx.createHttpServer().requestHandler(req -> {
      c.request(HttpMethod.GET, 8081, "localhost", "/", ctx.asyncAssertSuccess(clientReq -> {
        clientReq.send(ctx.asyncAssertSuccess(clientResp -> {
          req.response().end();
        }));
      }));
    }).listen(8080, ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    vertx.createHttpServer().requestHandler(req -> {
      req.response().end();
    }).listen(8081, ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req ->{
      req.send(ctx.asyncAssertSuccess(resp -> {
        responseLatch.complete();
      }));
    }));
    responseLatch.awaitSuccess();
    List<MockSpan> spans = waitUntil(3);
    assertSingleSpan(spans);
    MockSpan span = spans.get(0);
    assertEquals("GET", span.operationName());
    assertEquals("GET", span.tags().get("http.method"));
    assertEquals("http://localhost:8081/", span.tags().get("http.url"));
    assertEquals("200", span.tags().get("http.status_code"));
  }

  @Test
  public void testEventBus(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async(2);
    vertx.createHttpServer().requestHandler(req -> {
      vertx.eventBus().request("the-address", "ping", ctx.asyncAssertSuccess(resp -> {
        req.response().end();
      }));
    }).listen(8080, ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    vertx.eventBus().consumer("the-address", msg -> {
      msg.reply("pong");
    });
    vertx.createHttpServer().requestHandler(req -> {
      req.response().end();
    }).listen(8081, ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    HttpClient client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req ->{
      req.send(ctx.asyncAssertSuccess(resp -> {
        responseLatch.complete();
      }));
    }));
    responseLatch.awaitSuccess();
    List<MockSpan> spans = waitUntil(3);
    assertSingleSpan(spans);
    MockSpan span = spans.get(0);
    assertEquals("send", span.operationName());
    assertEquals("the-address", span.tags().get("peer.service"));
  }
}
