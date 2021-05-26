package io.vertx.tracing.opentelemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;
import io.opentelemetry.context.Scope;
import io.vertx.core.Vertx;

public class VertxContextStorageProvider implements ContextStorageProvider {

  static String ACTIVE_CONTEXT = "tracing.context";

  @Override
  public ContextStorage get() {
    return VertxContextStorage.INSTANCE;
  }

  enum VertxContextStorage implements ContextStorage {
    INSTANCE;

    @Override
    public Scope attach(Context toAttach) {
      return attach(Vertx.currentContext(), toAttach);
    }

    public Scope attach(io.vertx.core.Context vertxCtx, Context toAttach) {
      Context current = vertxCtx.getLocal(ACTIVE_CONTEXT);

      if (current == toAttach) {
        return Scope.noop();
      }

      vertxCtx.putLocal(ACTIVE_CONTEXT, toAttach);

      if (current == null) {
        return () -> vertxCtx.removeLocal(ACTIVE_CONTEXT);
      }
      return () -> vertxCtx.putLocal(ACTIVE_CONTEXT, current);
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
