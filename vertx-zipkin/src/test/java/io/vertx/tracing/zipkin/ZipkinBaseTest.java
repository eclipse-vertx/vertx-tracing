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
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Span;
import zipkin2.junit.ZipkinRule;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public abstract class ZipkinBaseTest {

  @Rule
  public ZipkinRule zipkin = new ZipkinRule();

  protected Vertx vertx;
  protected HttpClient client;

  @Before
  public void before() {
    String url = zipkin.httpUrl() + "/api/v2/spans";
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(
      new ZipkinTracingOptions()
        .setServiceName("my-service-name")
        .setSupportsJoin(false)
        .setSenderOptions(new HttpSenderOptions().setSenderEndpoint(url))
    ));
    client = vertx.createHttpClient();
  }

  @After
  public void after(TestContext ctx) {
    client.close();
    vertx.close(ctx.asyncAssertSuccess());
  }

  List<Span> waitUntilTrace(int min) throws Exception {
    return waitUntilTrace(zipkin, min);
  }

  static List<Span> waitUntilTrace(ZipkinRule zipkin, int min) throws Exception {
    long now = System.currentTimeMillis();
    while ((System.currentTimeMillis() - now) < 10000 ) {
      List<List<Span>> traces = zipkin.getTraces();
      if (traces.size() > 0 && traces.get(0).size() >= min) {
        return traces.get(0);
      }
      Thread.sleep(10);
    }
    throw new AssertionError();
  }

  List<Span> assertSingleSpan(List<Span> spans) {
    long result = spans.stream().map(Span::traceId).distinct().count();
    assertEquals(1, result);

    // Find top
    spans = new ArrayList<>(spans);
    Span top = spans.stream().filter(span -> span.id().equals(span.traceId())).findFirst().get();
    spans.remove(top);
    LinkedList<Span> sorted = foo(top, spans);
    sorted.addFirst(top);
    return sorted;
  }

  private LinkedList<Span> foo(Span top, List<Span> others) {
    if (others.isEmpty()) {
      return new LinkedList<>();
    }
    Span s = others.stream().filter(span -> span.parentId().equals(top.id())).findFirst().get();
    others.remove(s);
    LinkedList<Span> ret = foo(s, others);
    ret.addFirst(s);
    return ret;
  }
}
