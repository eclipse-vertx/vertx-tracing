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
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMapAdapter;
import io.opentracing.tag.Tags;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.tracing.TracingOptions;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class OpenTracingTest {

  private Vertx vertx;
  private MockTracer tracer;

  @Before
  public void before() {
    tracer = new MockTracer();
    vertx = Vertx.builder().withTracer(new OpenTracingTracerFactory(tracer)).build();
  }

  @After
  public void after(TestContext ctx) {
    vertx.close().onComplete(ctx.asyncAssertSuccess());
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
  public void testHttpServerRequestIgnorePolicy1(TestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.IGNORE), true, false);
  }

  @Test
  public void testHttpServerRequestIgnorePolicy2(TestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.IGNORE), false, false);
  }

  @Test
  public void testHttpServerRequestPropagatePolicy1(TestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE), true, true);
  }

  @Test
  public void testHttpServerRequestPropagatePolicy2(TestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE), false, false);
  }

  @Test
  public void testHttpServerRequestSupportPolicy1(TestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS), false, true);
  }

  @Test
  public void testHttpServerRequestSupportPolicy2(TestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS), true, true);
  }

  private void testHttpServerRequestPolicy(TestContext ctx,
                                           HttpServerOptions options,
                                           boolean createTrace,
                                           boolean expectTrace) throws Exception {
    Async listenLatch = ctx.async();
    vertx.createHttpServer(options).requestHandler(req -> {
      if (expectTrace) {
        ctx.assertNotNull(Vertx.currentContext().getLocal(OpenTracingUtil.ACTIVE_SPAN));
      } else {
        ctx.assertNull(Vertx.currentContext().getLocal(OpenTracingUtil.ACTIVE_SPAN));
      }
      req.response().end();
    }).listen(8080).onComplete(ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    listenLatch.awaitSuccess();
    sendRequest(createTrace);
    if (expectTrace) {
      List<MockSpan> spans = waitUntil(1);
      MockSpan span = spans.get(0);
      assertEquals("GET", span.operationName());
      assertEquals("GET", span.tags().get("http.method"));
      assertEquals("http://localhost:8080/", span.tags().get("http.url"));
      assertEquals("200", span.tags().get("http.status_code"));
    }
  }

  @Test
  public void testHttpClientRequestIgnorePolicy1(TestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.IGNORE, true, 1);
  }

  @Test
  public void testHttpClientRequestIgnorePolicy2(TestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.IGNORE, false, 0);
  }

  @Test
  public void testHttpClientRequestPropagatePolicy1(TestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.PROPAGATE, true, 3);
  }

  @Test
  public void testHttpClientRequestPropagatePolicy2(TestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.PROPAGATE, false, 0);
  }

  @Test
  public void testHttpClientRequestAlwaysPolicy1(TestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.ALWAYS, true, 3);
  }

  @Test
  public void testHttpClientRequestAlwaysPolicy2(TestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.ALWAYS, false, 2);
  }

  private List<MockSpan> testHttpClientRequest(TestContext ctx, TracingPolicy policy, boolean createTrace, int expectedTrace) throws Exception {
    Async listenLatch = ctx.async(2);
    HttpClient c = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(policy));
    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE)).requestHandler(req -> {
      c.request(HttpMethod.GET, 8081, "localhost", "/").onComplete(ctx.asyncAssertSuccess(clientReq -> {
        clientReq.send().onComplete(ctx.asyncAssertSuccess(clientResp -> {
          req.response().end();
        }));
      }));
    }).listen(8080).onComplete(ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE)).requestHandler(req -> {
      req.response().end();
    }).listen(8081).onComplete(ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    listenLatch.awaitSuccess();
    sendRequest(createTrace);
    Thread.sleep(1000);
    assertEquals(expectedTrace, tracer.finishedSpans().size());
    List<MockSpan> spans = tracer.finishedSpans();
    if (expectedTrace > 0) {
      assertSingleSpan(spans);
      Optional<MockSpan> opt = spans.stream().filter(span -> {
        try {
          assertEquals("GET", span.operationName());
          assertEquals("GET", span.tags().get("http.method"));
          assertEquals(createTrace ? "http://localhost:8080/" : "http://localhost:8081/", span.tags().get("http.url"));
          assertEquals("200", span.tags().get("http.status_code"));
          return true;
        } catch (AssertionError e) {
          return false;
        }
      }).findFirst();
      ctx.assertTrue(opt.isPresent());
    }
    return spans;
  }

  private void sendRequest(boolean withTrace) throws Exception {
    URL url = new URL("http://localhost:8080");
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    if (withTrace) {
      MockSpan span = tracer.buildSpan("test")
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .withTag(Tags.COMPONENT.getKey(), "vertx")
        .start();
      HashMap<String, String> headers = new HashMap<>();
      tracer.inject(span.context(), Format.Builtin.HTTP_HEADERS, new TextMapAdapter(headers));
      headers.forEach(con::setRequestProperty);
    }
    assertEquals(200, con.getResponseCode());
  }

  @Test
  public void testEventBus(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async(2);
    vertx.createHttpServer().requestHandler(req -> {
      vertx.eventBus().request("the-address", "ping").onComplete(ctx.asyncAssertSuccess(resp -> {
        req.response().end();
      }));
    }).listen(8080).onComplete(ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    vertx.eventBus().consumer("the-address", msg -> {
      msg.reply("pong");
    });
    vertx.createHttpServer().requestHandler(req -> {
      req.response().end();
    }).listen(8081).onComplete(ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    listenLatch.awaitSuccess();
    sendRequest(true);
    List<MockSpan> spans = waitUntil(3);
    assertSingleSpan(spans);
    MockSpan span = spans.get(0);
    assertEquals("send", span.operationName());
    assertEquals("the-address", span.tags().get("message_bus.destination"));
  }
}
