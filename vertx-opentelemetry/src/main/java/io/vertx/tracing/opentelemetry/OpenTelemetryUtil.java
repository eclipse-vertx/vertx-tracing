package io.vertx.tracing.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/**
 * OpenTracingContext adds helpers for associating and disassociating spans with the current {@link
 * Context}
 */
public final class OpenTelemetryUtil {
  static final String ACTIVE_SPAN = "vertx.tracing.opentelemetry.span";

  /**
   * Get the active span from the current {@link Context}
   * and return a {@link Span} or null
   */

  public static Span getSpan() {
    Context context = Vertx.currentContext();
    return context == null ? null : context.getLocal(ACTIVE_SPAN);
  }

  /**
   * Set the span as active on the context
   * @param span to associate with the context
   */
  public static void setSpan (Span span) {
    if(span != null) {
      Context context = Vertx.currentContext();
      if (context != null) {
        context.putLocal(ACTIVE_SPAN, span);
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
