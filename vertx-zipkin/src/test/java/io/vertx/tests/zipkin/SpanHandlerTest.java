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
package io.vertx.tests.zipkin;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import io.vertx.core.Vertx;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.tracing.zipkin.ZipkinTracer;
import io.vertx.tracing.zipkin.ZipkinTracerFactory;
import io.vertx.tracing.zipkin.ZipkinTracingOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

public class SpanHandlerTest {

  private Vertx vertx;

  @Before
  public void before() {
    vertx = Vertx.vertx();
  }

  @After
  public void after() throws Exception {
    vertx.close().await();
  }

  private static class CapturingSpanHandler extends SpanHandler {

    final List<MutableSpan> reported = new CopyOnWriteArrayList<>();

    @Override
    public boolean end(TraceContext context, MutableSpan span, Cause cause) {
      reported.add(span);
      return true;
    }
  }

  @Test
  public void testSpansAreReportedToTheSpanHandler() {
    CapturingSpanHandler handler = new CapturingSpanHandler();
    ZipkinTracer tracer = new ZipkinTracerFactory()
      .withSpanHandler(handler)
      .tracer(new ZipkinTracingOptions().setServiceName("the-service"));

    brave.Span span = tracer.receiveRequest(
      vertx.getOrCreateContext(),
      SpanKind.MESSAGING,
      TracingPolicy.ALWAYS,
      null,
      "the-operation",
      Collections.emptyList(),
      TagExtractor.empty()
    );
    tracer.sendResponse(vertx.getOrCreateContext(), null, span, null, TagExtractor.empty());
    tracer.close();

    assertNull(tracer.sender());
    assertEquals(1, handler.reported.size());
    assertEquals("the-operation", handler.reported.get(0).name());
    assertEquals("the-service", handler.reported.get(0).localServiceName());
  }
}
