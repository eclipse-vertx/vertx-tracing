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

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.util.Objects;

public final class Operation {

  private final Span span;
  private final Scope scope;

  public Operation(Span span, Scope scope) {
    this.span = Objects.requireNonNull(span);
    this.scope = Objects.requireNonNull(scope);
  }

  public Span span() {
    return span;
  }

  public Scope scope() {
    return scope;
  }
}
