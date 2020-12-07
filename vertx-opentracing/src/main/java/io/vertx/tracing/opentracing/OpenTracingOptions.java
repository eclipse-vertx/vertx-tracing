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

import io.opentracing.Tracer;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingOptions;

@DataObject
public class OpenTracingOptions extends TracingOptions {

  private Tracer tracer;

  public OpenTracingOptions(Tracer tracer) {
    this.tracer = tracer;
  }

  public OpenTracingOptions() {
  }

  public OpenTracingOptions(JsonObject json) {
    super(json);
  }

  io.vertx.core.spi.tracing.VertxTracer<?, ?> buildTracer() {
    if (tracer != null) {
      return new OpenTracingTracer(false, tracer);
    } else {
      return new OpenTracingTracer(true, OpenTracingTracer.createDefaultTracer());
    }
  }
}
