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
package io.vertx.tracing.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

class OpenTelemetryUtil {

  static String ACTIVE_CONTEXT = "tracing.context";
  static String ACTIVE_SPAN = "tracing.span";

  /**
   * Get the active span from the current {@link Context}
   *
   * @return a {@link Span} or null
   */
  static Span getSpan() {
    Context c = Vertx.currentContext();
    return c == null ? null : c.getLocal(ACTIVE_SPAN);
  }

  /**
   * Set the span as active on the context.
   *
   * @param span the span to associate with the context.
   */
  static void setSpan(Span span) {
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
  static void clearContext() {
    Context c = Vertx.currentContext();
    if (c != null) {
      c.removeLocal(ACTIVE_SPAN);
    }
  }
}
