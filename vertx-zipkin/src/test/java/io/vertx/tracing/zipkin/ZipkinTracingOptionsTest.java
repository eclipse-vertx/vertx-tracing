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

package io.vertx.tracing.zipkin;

import io.vertx.core.tracing.TracingOptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ZipkinTracingOptionsTest {

  @Test
  public void testCopy() {
    TracingOptions options = new ZipkinTracingOptions().setServiceName("foo");
    TracingOptions copy = options.copy();
    assertTrue(copy instanceof ZipkinTracingOptions);
    ZipkinTracingOptions other = (ZipkinTracingOptions) copy;
    assertEquals("foo", other.getServiceName());
  }
}
