/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.opentracing;

import io.opentracing.Span;
import io.opentracing.mock.MockTracer;
import io.vertx.core.Vertx;
import io.vertx.core.internal.ContextInternal;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.tracing.opentracing.OpenTracingTracerFactory;
import io.vertx.tracing.opentracing.OpenTracingUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static io.vertx.core.spi.context.storage.AccessMode.CONCURRENT;
import static io.vertx.tracing.opentracing.OpenTracingTracerFactory.ACTIVE_SPAN;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

@RunWith(VertxUnitRunner.class)
public class OpenTracingUtilTest {

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

  @Test
  public void getSpan_should_retrieve_a_span_from_the_currentContext(TestContext ctx) {
    Span span = tracer.buildSpan("test").start();
    vertx.runOnContext(ignored -> {
      assertNull(OpenTracingUtil.getSpan());
      ContextInternal context = (ContextInternal) Vertx.currentContext();
      context.putLocal(ACTIVE_SPAN, CONCURRENT, span);

      assertSame(span, OpenTracingUtil.getSpan());
    });
  }

  @Test
  public void getSpan_should_return_null_when_there_is_no_current_context(TestContext ctx) {
    Span span = tracer.buildSpan("test").start();
    OpenTracingUtil.setSpan(span);
    assertNull(OpenTracingUtil.getSpan());
  }

  @Test
  public void setSpan_should_put_the_span_on_the_current_context() {
    Span span = tracer.buildSpan("test").start();
    vertx.runOnContext(ignored -> {
      assertNull(OpenTracingUtil.getSpan());
      OpenTracingUtil.setSpan(span);

      ContextInternal context = (ContextInternal) Vertx.currentContext();
      assertSame(span, context.getLocal(ACTIVE_SPAN));
    });
  }

  @Test
  public void clearContext_should_remove_any_span_from_the_context() {
    Span span = tracer.buildSpan("test").start();
    vertx.runOnContext(ignored -> {
      assertNull(OpenTracingUtil.getSpan());
      OpenTracingUtil.setSpan(span);

      OpenTracingUtil.clearContext();
      assertNull(OpenTracingUtil.getSpan());
    });
  }
}
