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
package io.vertx.tracing.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;

@DataObject
public class OpenTelemetryOptions extends TracingOptions {

  private Tracer tracer;

  public OpenTelemetryOptions(Tracer tracer) {
    this.tracer = tracer;
  }

  public OpenTelemetryOptions() {
  }

  public OpenTelemetryOptions(JsonObject json) {
    super(json);
  }

  VertxTracer<Span, Span> buildTracer() {
    if (tracer != null) {
      return new OpenTelemetryTracer(tracer);
    } else {
      return new OpenTelemetryTracer(Tracer.getDefault());
    }
  }

}
