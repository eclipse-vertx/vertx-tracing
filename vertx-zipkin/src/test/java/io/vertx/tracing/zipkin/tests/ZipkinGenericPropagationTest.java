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
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.tracing.zipkin.ZipkinTracer;
import org.junit.Test;
import zipkin2.Span;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;

public class ZipkinGenericPropagationTest extends ZipkinBaseTest {

  @Test
  public void serverToClient(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async();
    vertx.createHttpServer().requestHandler(req -> {
      ContextInternal current = (ContextInternal) Vertx.currentContext();
      VertxTracer tracer = current.tracer();
      TraceContext requestContext = current.getLocal(ZipkinTracer.ACTIVE_CONTEXT);
      Object request = new Object();
      Map<String, String> headers = new HashMap<>();
      Object trace = tracer.sendRequest(current, SpanKind.RPC, TracingPolicy.PROPAGATE, request, "my_op", (BiConsumer<String, String>) headers::put, TagExtractor.empty());
      ctx.assertEquals(requestContext.traceIdString(), headers.get("X-B3-TraceId"));
      ctx.assertNotNull(headers.get("X-B3-SpanId"));
      ctx.assertEquals(requestContext.spanIdString(), headers.get("X-B3-ParentSpanId"));
      current.setTimer(10, id -> {
        Object response = new Object();
        tracer.receiveResponse(current, response, trace, null, TagExtractor.empty());
        req.response().end();
      });
    }).listen(8080).onComplete(ctx.asyncAssertSuccess(v -> listenLatch.complete()));
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(TracingPolicy.ALWAYS));
    client.request(HttpMethod.GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
      req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(200, resp.statusCode());
        responseLatch.complete();
      }));
    }));
    responseLatch.awaitSuccess();
    List<Span> trace = assertSingleSpan(waitUntilTrace(3));
    assertEquals(3, trace.size());
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
  }

  @Test
  public void clientToServer(TestContext ctx) throws Exception {
    VertxInternal vertx = (VertxInternal) this.vertx;
    ContextInternal current = vertx.getOrCreateContext();
    VertxTracer tracer = current.tracer();
    Map<String, String> headers = new HashMap<>();
    Object clientTrace = tracer.sendRequest(current, SpanKind.RPC, TracingPolicy.ALWAYS, new Object(), "foo", (BiConsumer<String, String>) headers::put, TagExtractor.empty());
    ContextInternal receiving = vertx.createEventLoopContext();
    Async responseLatch = ctx.async();
    receiving.runOnContext(v -> {
      ContextInternal duplicate = receiving.duplicate();
      Object serverTrace = tracer.receiveRequest(duplicate, SpanKind.RPC, TracingPolicy.PROPAGATE, new Object(), "bar", headers.entrySet(), TagExtractor.empty());
      tracer.sendResponse(duplicate, new Object(), serverTrace, null, TagExtractor.empty());
      responseLatch.complete();
    });
    responseLatch.await(20_000);
    tracer.receiveResponse(current, new Object(), clientTrace, null, TagExtractor.empty());
    List<Span> trace = assertSingleSpan(waitUntilTrace(2));
    Span span1 = trace.get(0);
    assertEquals(Span.Kind.CLIENT, span1.kind());
    assertEquals("my-service-name", span1.localServiceName());
    assertEquals("foo", span1.name());
    Span span2 = trace.get(1);
    assertEquals(Span.Kind.SERVER, span2.kind());
    assertEquals("my-service-name", span2.localServiceName());
    assertEquals("bar", span2.name());
  }
}
