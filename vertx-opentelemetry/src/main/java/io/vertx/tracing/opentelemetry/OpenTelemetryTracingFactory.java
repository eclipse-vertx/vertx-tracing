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
package io.vertx.tracing.opentelemetry;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

public class OpenTelemetryTracingFactory implements VertxTracerFactory {

  static final ContextLocal<Context> ACTIVE_CONTEXT = ContextLocal.registerLocal(Context.class);

  private final OpenTelemetry openTelemetry;
  private SpanCustomizer spanCustomizer;

  public OpenTelemetryTracingFactory() {
    this(null);
  }

  public OpenTelemetryTracingFactory(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Set a customizer invoked before the tracer starts a span, e.g. to add custom attributes visible to the sampler.
   *
   * @param spanCustomizer the customizer, or {@code null} for none
   * @return a reference to this, so the API can be used fluently
   */
  public OpenTelemetryTracingFactory withSpanCustomizer(SpanCustomizer spanCustomizer) {
    this.spanCustomizer = spanCustomizer;
    return this;
  }

  @Override
  public VertxTracer<?, ?> tracer(final TracingOptions options) {
    if (openTelemetry != null) {
      return new OpenTelemetryTracer(openTelemetry, spanCustomizer);
    } else {
      return new OpenTelemetryTracer(GlobalOpenTelemetry.get(), spanCustomizer);
    }
  }

  @Override
  public OpenTelemetryOptions newOptions() {
    return new OpenTelemetryOptions();
  }

  @Override
  public OpenTelemetryOptions newOptions(JsonObject jsonObject) {
    return new OpenTelemetryOptions(jsonObject);
  }
}
