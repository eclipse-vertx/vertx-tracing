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

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.junit.ZipkinRule;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ZipkinHttpTest extends ZipkinBaseTest {

  @Test
  public void testHttpServerRequest(TestContext ctx) throws Exception {
    testHttpServerRequest(zipkin, vertx, ctx);
  }

  public static void testHttpServerRequest(ZipkinRule zipkin, Vertx vertx, TestContext ctx) throws Exception {
    Async listenLatch = ctx.async();
    vertx.createHttpServer().requestHandler(req -> {
      req.response().end();
    }).listen(8080, ctx.asyncAssertSuccess(v -> listenLatch.complete()));
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    HttpClient client = vertx.createHttpClient();
    try {
      client.request(HttpMethod.GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req ->{
        req.send(ctx.asyncAssertSuccess(resp -> {
          responseLatch.complete();
        }));
      }));
      responseLatch.awaitSuccess();
      List<Span> trace = waitUntilTrace(zipkin, 2);
      assertEquals(2, trace.size());
      Span span1 = trace.get(0);
      Span span2 = trace.get(1);
//    assertEquals("get", span.name());
//    assertEquals("GET", span.tags().get("http.method"));
//    assertEquals("/", span.tags().get("http.path"));
      responseLatch.await(10000);
    } finally {
      client.close();
    }
  }

  @Test
  public void testHttpClientRequest(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async(2);
    HttpClient c = vertx.createHttpClient();
    HttpServer server = vertx.createHttpServer().requestHandler(req -> {
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
    Async responseLatch = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(clientReq ->{
      clientReq.send(ctx.asyncAssertSuccess(clientResp -> {
        responseLatch.complete();
      }));
    }));
    responseLatch.awaitSuccess();
    List<Span> trace = assertSingleSpan(waitUntilTrace(4));
    assertEquals(4, trace.size());
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
}
