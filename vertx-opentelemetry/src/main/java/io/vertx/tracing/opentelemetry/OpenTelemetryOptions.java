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

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

@DataObject
public class OpenTelemetryOptions extends TracingOptions {

  private OpenTelemetry openTelemetry;

  public OpenTelemetryOptions(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public OpenTelemetryOptions() {
  }

  public OpenTelemetryOptions(JsonObject json) {
    super(json);
  }

  VertxTracer<Span, Span> buildTracer() {
    if (openTelemetry != null) {
      return new OpenTelemetryTracer(openTelemetry);
    } else {
      return new OpenTelemetryTracer(GlobalOpenTelemetry.get());
    }
  }

}
