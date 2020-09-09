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

import io.opentracing.Span;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * OpenTracingContext adds helpers for associating and disassociating spans with the current {@link
 * Context}
 */
public final class OpenTracingUtil {

  static final String ACTIVE_SPAN = "vertx.tracing.opentracing.span";

  /**
   * Get the active span from the current {@link Context}
   *
   * @return a {@link Span} or null
   */
  public static Span getSpan() {
    Context c = Vertx.currentContext();
    return c == null ? null : c.getLocal(ACTIVE_SPAN);
  }

  /**
   * Set the span as active on the context.
   *
   * @param span the span to associate with the context.
   */
  public static void setSpan(Span span) {
    if (span != null) {
      Context c = Vertx.currentContext();
      if (c != null) {
        c.putLocal(ACTIVE_SPAN, span);
      }
    }
  }

  /**
   * Remove any active span on the context.
   */
  public static void clearContext() {
    Context c = Vertx.currentContext();
    if (c != null) {
      c.removeLocal(ACTIVE_SPAN);
    }
  }
}
