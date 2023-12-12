/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tracing.opentelemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.ContextStorageProvider;
import io.opentelemetry.context.Scope;
import io.vertx.core.impl.ContextInternal;

public class VertxContextStorageProvider implements ContextStorageProvider {

  private static final Object ACTIVE_CONTEXT = new Object();

  @Override
  public ContextStorage get() {
    return VertxContextStorage.INSTANCE;
  }

  enum VertxContextStorage implements ContextStorage {
    INSTANCE;

    @Override
    public Scope attach(Context toAttach) {
      ContextInternal current = ContextInternal.current();
      if (current == null) {
        return ContextStorage.defaultStorage().attach(toAttach);
      }
      return attach(current, toAttach);
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
      ContextInternal vertxCtx = ContextInternal.current();
      if (vertxCtx == null) {
        return ContextStorage.defaultStorage().current();
      }
      return vertxCtx.getLocal(ACTIVE_CONTEXT);
    }
  }
}
