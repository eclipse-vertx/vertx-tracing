/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tracing.opentracing;

import io.opentracing.mock.MockTracer;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.tracing.TracingOptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class OpenTracingOptionsTest {

  @Test
  public void testCopy() {
    MockTracer tracer = new MockTracer();
    TracingOptions options = new OpenTracingOptions(tracer);
    TracingOptions copy = options.copy();
    assertTrue(copy instanceof OpenTracingOptions);
    OpenTracingOptions other = (OpenTracingOptions) copy;
    assertSame(tracer, other.getTracer());
  }

  @Test
  public void testDefaultFactory() {
    TracingOptions options = new OpenTracingOptions();
    assertNotNull(options.getFactory());
    assertEquals(OpenTracingTracerFactory.INSTANCE, options.getFactory());
  }

  @Test
  public void testFactory() {
    TracingOptions options = new OpenTracingOptions().setFactory(VertxTracerFactory.NOOP);
    assertNotNull(options.getFactory());
    assertNotEquals(OpenTracingTracerFactory.INSTANCE, options.getFactory());
    assertEquals(VertxTracerFactory.NOOP, options.getFactory());
  }
}
