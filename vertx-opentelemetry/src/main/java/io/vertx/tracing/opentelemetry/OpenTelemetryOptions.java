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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

@DataObject
public class OpenTelemetryOptions extends TracingOptions {

  private OpenTelemetry openTelemetry;

  /**
   * @deprecated instead use {@link io.vertx.core.VertxBuilder#withTracer(VertxTracerFactory)}
   * and {@link OpenTelemetryTracer#OpenTelemetryTracer(OpenTelemetry)}.
   */
  @Deprecated
  public OpenTelemetryOptions(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
    this.setFactory(OpenTelemetryTracingFactory.INSTANCE);
  }

  public OpenTelemetryOptions() {
    this.setFactory(OpenTelemetryTracingFactory.INSTANCE);
  }

  public OpenTelemetryOptions(JsonObject json) {
    super(json);
    this.setFactory(OpenTelemetryTracingFactory.INSTANCE);
  }

  VertxTracer<Operation, Operation> buildTracer() {
    if (openTelemetry != null) {
      return new OpenTelemetryTracer(openTelemetry);
    } else {
      return new OpenTelemetryTracer(GlobalOpenTelemetry.get());
    }
  }

}
