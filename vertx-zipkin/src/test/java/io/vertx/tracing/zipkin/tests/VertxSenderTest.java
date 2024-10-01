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

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.tracing.zipkin.ZipkinTracingOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Span;
import zipkin2.junit.ZipkinRule;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class VertxSenderTest {

  private Vertx vertx;

  @Before
  public void before() {
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new ZipkinTracingOptions()));
  }

  @After
  public void after(TestContext ctx) {
    vertx.close().onComplete(ctx.asyncAssertSuccess());
  }

  @Test
  public void testDefaultSenderEndpoint(TestContext ctx) throws Exception {
    ZipkinRule zipkin = new ZipkinRule();
    zipkin.start(9411);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(TracingPolicy.ALWAYS));
    try {
      Async listenLatch = ctx.async();
      vertx.createHttpServer().requestHandler(req -> {
        req.response().end();
      }).listen(8080).onComplete(ctx.asyncAssertSuccess(v -> listenLatch.complete()));
      listenLatch.awaitSuccess();
      Async responseLatch = ctx.async();
      client.request(HttpMethod.GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
        req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
          responseLatch.complete();
        }));
      }));
      responseLatch.awaitSuccess();
      List<Span> trace = ZipkinBaseTest.waitUntilTrace(zipkin, 2);
      assertEquals(2, trace.size());
      responseLatch.await(10000);
    } finally {
      client.close();
      zipkin.shutdown();
    }
  }
}
