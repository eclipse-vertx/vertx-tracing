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

import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

public class OpenTelemetryTracingFactory implements VertxTracerFactory {

  static final OpenTelemetryTracingFactory INSTANCE = new OpenTelemetryTracingFactory();

  @Override
  public VertxTracer<?, ?> tracer(final TracingOptions options) {
    OpenTelemetryOptions openTelemetryOptions;
    if (options instanceof OpenTelemetryOptions) {
      openTelemetryOptions = (OpenTelemetryOptions) options;
    } else {
      openTelemetryOptions = new OpenTelemetryOptions(options.toJson());
    }
    return openTelemetryOptions.buildTracer();
  }

  @Override
  public TracingOptions newOptions() {
    return new OpenTelemetryOptions();
  }

  @Override
  public TracingOptions newOptions(JsonObject jsonObject) {
    return new OpenTelemetryOptions(jsonObject);
  }
}
