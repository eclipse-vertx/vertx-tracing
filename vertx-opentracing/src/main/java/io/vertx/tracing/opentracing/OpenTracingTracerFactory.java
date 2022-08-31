/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tracing.opentracing;

import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

public class OpenTracingTracerFactory implements VertxTracerFactory {

  static final OpenTracingTracerFactory INSTANCE = new OpenTracingTracerFactory();

  @Override
  public VertxTracer tracer(TracingOptions options) {
    OpenTracingOptions openTracingOptions;
    if (options instanceof OpenTracingOptions) {
      openTracingOptions = (OpenTracingOptions) options;
    } else {
      openTracingOptions = new OpenTracingOptions(options.toJson());
    }
    return openTracingOptions.buildTracer();
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
