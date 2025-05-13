/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
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
import io.opentracing.Tracer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

public class OpenTracingTracerFactory implements VertxTracerFactory {

  public static final ContextLocal<Span> ACTIVE_SPAN = ContextLocal.registerLocal(Span.class);

  private final Tracer tracer;

  public OpenTracingTracerFactory() {
    this(null);
  }

  public OpenTracingTracerFactory(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public VertxTracer tracer(TracingOptions options) {
    if (tracer != null) {
      return new OpenTracingTracer(false, tracer);
    } else {
      return new OpenTracingTracer(true, OpenTracingTracer.createDefaultTracer());
    }
  }

  @Override
  public OpenTracingOptions newOptions() {
    return new OpenTracingOptions();
  }

  @Override
  public OpenTracingOptions newOptions(JsonObject jsonObject) {
    return new OpenTracingOptions(jsonObject);
  }
}
