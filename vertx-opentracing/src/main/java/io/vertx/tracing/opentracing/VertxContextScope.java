package io.vertx.tracing.opentracing;

import io.opentracing.Scope;
import io.opentracing.Span;

/**
 * VertxContextScope is based on the {@link io.opentracing.util.ThreadLocalScope}.
 */
public class VertxContextScope implements Scope {

  private final VertxContextScopeManager scopeManager;
  private final Span wrapped;
  private final VertxContextScope toRestore;

  /**
   * Construct a {@link Scope} based on the current vertx context.
   *
   * @param scopeManager scope manager for this scope.
   * @param wrapped the span to wrap.
   */
  VertxContextScope(VertxContextScopeManager scopeManager, Span wrapped) {
    this.scopeManager = scopeManager;
    this.wrapped = wrapped;
    this.toRestore = scopeManager.getScope();
    scopeManager.setScope(this);
  }

  @Override
  public void close() {
    if (scopeManager.getScope() != this) {
      // This shouldn't happen if users call methods in the expected order. Bail out.
      return;
    }
    scopeManager.setScope(toRestore);
  }

  /**
   * Access to the wrapped span for use in the {@link VertxContextScopeManager}
   *
   * @return wrapped span
   */
  Span span() {
    return wrapped;
  }
}
