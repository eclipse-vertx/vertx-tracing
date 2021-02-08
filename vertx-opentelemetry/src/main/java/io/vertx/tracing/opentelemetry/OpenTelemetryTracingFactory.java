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
import io.opentelemetry.api.trace.Tracer;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

public class OpenTelemetryTracingFactory implements VertxTracerFactory {

  private final Tracer tracer;

  public OpenTelemetryTracingFactory() {
    this.tracer = Tracer.getDefault();
  }

  public OpenTelemetryTracingFactory(final Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public VertxTracer<Span, Span> tracer(final TracingOptions options) {
    OpenTelemetryOptions openTelemetryOptions;
    if (options instanceof OpenTelemetryOptions) {
      openTelemetryOptions = (OpenTelemetryOptions) options;
    } else {
      openTelemetryOptions = new OpenTelemetryOptions();
    }
    return openTelemetryOptions.buildTracer();
  }
}
