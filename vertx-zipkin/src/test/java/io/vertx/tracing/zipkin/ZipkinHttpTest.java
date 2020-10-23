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

import brave.Tracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;
import zipkin2.Span;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ZipkinHttpTest extends ZipkinBaseTest {

  @Test
  public void testHttpServerRequestIgnorePolicy1(TestContext ctx) throws Exception {
    testHttpServerRequest(ctx, TracingPolicy.IGNORE, true, 0);
  }

  @Test
  public void testHttpServerRequestIgnorePolicy2(TestContext ctx) throws Exception {
    testHttpServerRequest(ctx, TracingPolicy.IGNORE, false, 0);
  }

  @Test
  public void testHttpServerRequestPropagatePolicy1(TestContext ctx) throws Exception {
    testHttpServerRequest(ctx, TracingPolicy.PROPAGATE, true, 2);
  }

  @Test
  public void testHttpServerRequestPropagatePolicy2(TestContext ctx) throws Exception {
    testHttpServerRequest(ctx, TracingPolicy.PROPAGATE, false, 0);
  }

  @Test
  public void testHttpServerRequestSupportPolicy1(TestContext ctx) throws Exception {
    testHttpServerRequest(ctx, TracingPolicy.ALWAYS, true, 2);
  }

  @Test
  public void testHttpServerRequestSupportPolicy2(TestContext ctx) throws Exception {
    testHttpServerRequest(ctx, TracingPolicy.ALWAYS, false, 1);
  }

  void testHttpServerRequest(TestContext ctx, TracingPolicy policy, boolean withTrace, int expectedSpans) throws Exception {
    Async listenLatch = ctx.async();
    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(policy)).requestHandler(req -> {
      req.response().end();
    }).listen(8080, ctx.asyncAssertSuccess(v -> listenLatch.complete()));
    listenLatch.awaitSuccess();
    sendRequest(withTrace);
    if (expectedSpans > 0) {
      List<Span> trace = waitUntilTrace(zipkin, expectedSpans);
      assertEquals(expectedSpans, trace.size());
//      Span span1 = trace.get(0);
//      Span span2 = trace.get(1);
//    assertEquals("get", span.name());
//    assertEquals("GET", span.tags().get("http.method"));
//    assertEquals("/", span.tags().get("http.path"));
    } else {
      assertEquals(0, zipkin.getTraces().size());
    }
  }

  @Test
  public void testHttpClientRequestIgnorePolicy1(TestContext ctx) throws Exception {
    List<Span> trace = testHttpClientRequest(ctx, TracingPolicy.IGNORE, true, 2);
    Span span1 = trace.get(0);
    assertEquals(Span.Kind.CLIENT, span1.kind());
    assertEquals("my-service-name", span1.localServiceName());
    assertEquals("get", span1.name());
    assertEquals("GET", span1.tags().get("http.method"));
    assertEquals("/", span1.tags().get("http.path"));
    assertEquals(8080, span1.remoteEndpoint().portAsInt());
    Span span2 = trace.get(1);
    assertEquals(Span.Kind.SERVER, span2.kind());
    assertEquals("get", span2.name());
    assertEquals("GET", span2.tags().get("http.method"));
    assertEquals("/", span2.tags().get("http.path"));
  }

  @Test
  public void testHttpClientRequestIgnorePolicy2(TestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.IGNORE, false, 0);
  }

  @Test
  public void testHttpClientRequestPropagatePolicy1(TestContext ctx) throws Exception {
    List<Span> trace = testHttpClientRequest(ctx, TracingPolicy.PROPAGATE, true, 4);
    Span span1 = trace.get(0);
    assertEquals(Span.Kind.CLIENT, span1.kind());
    assertEquals("my-service-name", span1.localServiceName());
    assertEquals("get", span1.name());
    assertEquals("GET", span1.tags().get("http.method"));
    assertEquals("/", span1.tags().get("http.path"));
    assertEquals(8080, span1.remoteEndpoint().portAsInt());
    Span span2 = trace.get(1);
    assertEquals(Span.Kind.SERVER, span2.kind());
    assertEquals("get", span2.name());
    assertEquals("GET", span2.tags().get("http.method"));
    assertEquals("/", span2.tags().get("http.path"));
    Span span3 = trace.get(2);
    assertEquals(Span.Kind.CLIENT, span3.kind());
    assertEquals("get", span3.name());
    assertEquals("GET", span3.tags().get("http.method"));
    assertEquals("/", span3.tags().get("http.path"));
    assertEquals(8081, span3.remoteEndpoint().portAsInt());
    Span span4 = trace.get(3);
    assertEquals(Span.Kind.SERVER, span4.kind());
    assertEquals("get", span4.name());
    assertEquals("GET", span4.tags().get("http.method"));
    assertEquals("/", span4.tags().get("http.path"));
  }

  @Test
  public void testHttpClientRequestPropagatePolicy2(TestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.PROPAGATE, false, 0);
  }

  @Test
  public void testHttpClientRequestSupportPolicy1(TestContext ctx) throws Exception {
    List<Span> trace = testHttpClientRequest(ctx, TracingPolicy.ALWAYS, true, 4);
    Span span1 = trace.get(0);
    assertEquals(Span.Kind.CLIENT, span1.kind());
    assertEquals("my-service-name", span1.localServiceName());
    assertEquals("get", span1.name());
    assertEquals("GET", span1.tags().get("http.method"));
    assertEquals("/", span1.tags().get("http.path"));
    assertEquals(8080, span1.remoteEndpoint().portAsInt());
    Span span2 = trace.get(1);
    assertEquals(Span.Kind.SERVER, span2.kind());
    assertEquals("get", span2.name());
    assertEquals("GET", span2.tags().get("http.method"));
    assertEquals("/", span2.tags().get("http.path"));
    Span span3 = trace.get(2);
    assertEquals(Span.Kind.CLIENT, span3.kind());
    assertEquals("get", span3.name());
    assertEquals("GET", span3.tags().get("http.method"));
    assertEquals("/", span3.tags().get("http.path"));
    assertEquals(8081, span3.remoteEndpoint().portAsInt());
    Span span4 = trace.get(3);
    assertEquals(Span.Kind.SERVER, span4.kind());
    assertEquals("get", span4.name());
    assertEquals("GET", span4.tags().get("http.method"));
    assertEquals("/", span4.tags().get("http.path"));
  }

  @Test
  public void testHttpClientRequestSupportPolicy2(TestContext ctx) throws Exception {
    List<Span> trace = testHttpClientRequest(ctx, TracingPolicy.ALWAYS, false, 2);
    Span span1 = trace.get(0);
    assertEquals(Span.Kind.CLIENT, span1.kind());
    assertEquals("get", span1.name());
    assertEquals("GET", span1.tags().get("http.method"));
    assertEquals("/", span1.tags().get("http.path"));
    assertEquals(8081, span1.remoteEndpoint().portAsInt());
    Span span2 = trace.get(1);
    assertEquals(Span.Kind.SERVER, span2.kind());
    assertEquals("get", span2.name());
    assertEquals("GET", span2.tags().get("http.method"));
    assertEquals("/", span2.tags().get("http.path"));
  }

  private List<Span> testHttpClientRequest(TestContext ctx, TracingPolicy policy, boolean withTrace, int expectedSpans) throws Exception {
    Async listenLatch = ctx.async(2);
    HttpClient c = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(policy));
    vertx.createHttpServer().requestHandler(req -> {
      c.request(HttpMethod.GET, 8081, "localhost", "/", ctx.asyncAssertSuccess(clientReq -> {
        clientReq.send(ctx.asyncAssertSuccess(clientResp -> {
          req.response().end();
        }));
      }));
    }).listen(8080, ar -> {
      ctx.assertTrue(ar.succeeded(), "Could not bind on port 8080");
      listenLatch.countDown();
    });
    vertx.createHttpServer().requestHandler(req -> {
      req.response().end();
    }).listen(8081, ar -> {
      ctx.assertTrue(ar.succeeded(), "Could not bind on port 8081");
      listenLatch.countDown();
    });
    listenLatch.awaitSuccess();
    sendRequest(withTrace);
    if (expectedSpans > 0) {
      List<Span> trace = assertSingleSpan(waitUntilTrace(expectedSpans));
      assertEquals(expectedSpans, trace.size());
      return trace;
    } else {
      assertEquals(0, zipkin.getTraces().size());
      return Collections.emptyList();
    }
  }

  private void sendRequest(boolean withTrace) throws Exception {
    URL url = new URL("http://localhost:8080");
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    if (withTrace) {
      ZipkinTracer tracer = (ZipkinTracer) ((ContextInternal) vertx.getOrCreateContext()).tracer();
      Tracing tracing = tracer.getTracing();
      brave.Span span = tracing.tracer().newTrace();
      span.kind(brave.Span.Kind.CLIENT);
      span.name("get");
      span.tag("http.method", "GET");
      span.tag("http.path", "/");
      span.remoteIpAndPort("127.0.0.1", 8080);
      TraceContext.Injector<HttpURLConnection> injector = tracing.propagation().injector(new Propagation.Setter<HttpURLConnection, String>() {
        @Override
        public void put(HttpURLConnection c, String key, String value) {
          c.setRequestProperty(key, value);
        }
      });
      injector.inject(span.context(), con);
      assertEquals(200, con.getResponseCode());
      span.finish();
    } else {
      assertEquals(200, con.getResponseCode());
    }
  }
}
