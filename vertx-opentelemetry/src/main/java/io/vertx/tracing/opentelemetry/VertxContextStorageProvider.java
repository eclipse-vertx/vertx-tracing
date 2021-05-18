package io.vertx.tracing.opentelemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;
import io.opentelemetry.context.Scope;
import io.vertx.core.Vertx;

public class VertxContextStorageProvider implements ContextStorageProvider {

  static String ACTIVE_CONTEXT = "tracing.context";
  static String ACTIVE_SPAN = "tracing.span";

  @Override
  public ContextStorage get() {
    return VertxContextStorage.INSTANCE;
  }

  private enum VertxContextStorage implements ContextStorage {
    INSTANCE;

    @Override
    public Scope attach(Context toAttach) {
      io.vertx.core.Context vertxCtx = Vertx.currentContext();
      Context current = vertxCtx.getLocal(ACTIVE_CONTEXT);

      if (current == toAttach) {
        return Scope.noop();
      }

      vertxCtx.putLocal(ACTIVE_CONTEXT, toAttach);
      return () -> vertxCtx.removeLocal(ACTIVE_CONTEXT);
    }

    @Override
    public Context current() {
      io.vertx.core.Context vertxCtx = Vertx.currentContext();
      if (vertxCtx == null) {
        return null;
      }
      return vertxCtx.getLocal(ACTIVE_CONTEXT);
    }
  }
}
