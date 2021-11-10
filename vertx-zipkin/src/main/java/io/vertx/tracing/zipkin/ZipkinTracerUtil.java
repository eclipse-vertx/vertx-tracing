package io.vertx.tracing.zipkin;

import brave.Span;
import brave.propagation.B3SingleFormat;
import brave.propagation.TraceContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

public final class ZipkinTracerUtil {
  public static final String ACTIVE_SPAN = "vertx.tracing.zipkin.active_span";
  public static final String ACTIVE_CONTEXT = "vertx.tracing.zipkin.active_context";	  
	
  /**
  * @return the current active {@link Span} otherwise {@code null}
  */
  public static Span getSpan() {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      return ctx.getLocal(ACTIVE_SPAN);
    }
    return null;
  }

  /**
   * @return the current active {@link TraceContext} otherwise {@code null}
   */
  public static TraceContext getTraceContext() {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      return ctx.getLocal(ACTIVE_CONTEXT);
    }
    return null;
  }
  
  /**
   * Remove any active context.
   */
  public static void clearContext() {
    Context c = Vertx.currentContext();
    if (c != null) {
      c.removeLocal(ACTIVE_CONTEXT);
    }
  }
  
  /**
   * Remove any active span.
   */
  public static void clearSpan() {
    Context c = Vertx.currentContext();
    if (c != null) {
      c.removeLocal(ACTIVE_SPAN);
    }
  }
  
  /**
   * Import traceId.
   */
  public static void importTraceId(String traceId) {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      ctx.putLocal(ACTIVE_CONTEXT, B3SingleFormat.parseB3SingleFormat(traceId).context());
    }
  }
  
  /**
   * Export active traceId otherwise {@code null}.
   */
  public static String exportTraceId() {
    TraceContext ctx = getTraceContext();
    if (ctx != null) {
      return B3SingleFormat.writeB3SingleFormat(ctx);
    }
    return null;
  }
  
  /**
   * Set active {@link TraceContext}.
   */
  public static void setTraceContext(TraceContext context) {
	Context ctx = Vertx.currentContext();
	if(ctx != null) {
      ctx.putLocal(ACTIVE_CONTEXT, context);
	}
  }

  /**
   * Set active {@link Span}.
   */
  public static void setSpan(Span span) {
    Context ctx = Vertx.currentContext();
	if(ctx != null) {
      ctx.putLocal(ACTIVE_SPAN, span);
	}	
  }
}
