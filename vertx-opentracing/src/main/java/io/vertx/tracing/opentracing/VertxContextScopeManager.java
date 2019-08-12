package io.vertx.tracing.opentracing;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

/** VertxContextScopeManager is based on the {@link io.opentracing.util.ThreadLocalScopeManager}. */
public class VertxContextScopeManager implements ScopeManager {

  private static final String ACTIVE_SCOPE = "vertx.tracing.opentracing.scope";

  private static final CapturedContext capturedContext = new CapturedContext();

  /**
   * Capture a {@link Context} instance to use for scope management. This is useful when a Context
   * is not attached to a Thread, which would cause {@code Vertx.currentContext()} to return null.
   *
   * @return the captured context instance.
   */
  static CapturedContext withContext(Context toCapture) {
    capturedContext.set(toCapture);
    return capturedContext;
  }

  private Context getContext() {
    Context c = Vertx.currentContext();
    return c != null ? c : capturedContext.get();
  }

  /**
   * Set the active {@link VertxContextScope} for the current {@link Context}
   *
   * @param scope the scope to set as active.
   * @return the scope that was set as active.
   */
  VertxContextScope setScope(VertxContextScope scope) {
    Context context = getContext();
    if (context == null) {
      return scope;
    }
    if (scope == null) {
      context.removeLocal(ACTIVE_SCOPE);
    } else {
      context.putLocal(ACTIVE_SCOPE, scope);
    }
    return scope;
  }

  /**
   * Get the active {@link VertxContextScope} for the current {@link Context}
   *
   * @return active scope.
   */
  VertxContextScope getScope() {
    Context context = getContext();
    return context == null ? null : context.getLocal(ACTIVE_SCOPE);
  }

  @Override
  public Scope activate(Span span) {
    VertxContextScope scope = new VertxContextScope(this, span);
    return setScope(scope);
  }

  @Override
  public Span activeSpan() {
    VertxContextScope scope = getScope();
    return scope == null ? null : scope.span();
  }

  /**
   * A helper class for using a passed context for the VertxContextScopeManager. There are times
   * where a {@link Context} exists, but it is not attached to a thread. This class allows for
   * capturing the context in a try with resources block.
   */
  static class CapturedContext implements AutoCloseable {

    private final ThreadLocal<Context> cache;

    /** Create an instance of a Captured {@link Context}. */
    CapturedContext() {
      cache = new ThreadLocal<>();
    }

    /**
     * Set the captured {@link Context}
     *
     * @return the captured context.
     */
    private void set(Context toCapture) {
      cache.set(toCapture);
    }

    /**
     * Get the captured {@link Context}
     *
     * @return the captured context.
     */
    Context get() {
      return cache.get();
    }

    @Override
    public void close() {
      try {
        // remove the captured Context
        cache.remove();
      } catch (Exception e) {
      }
    }
  }
}
