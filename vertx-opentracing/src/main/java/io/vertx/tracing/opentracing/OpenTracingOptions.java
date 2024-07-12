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
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.tracing.TracingOptions;

@DataObject
public class OpenTracingOptions extends TracingOptions {

  private Tracer tracer;

  public OpenTracingOptions() {
    this.setFactory(OpenTracingTracerFactory.INSTANCE);
  }

  /**
   * @deprecated instead use {@link io.vertx.core.VertxBuilder#withTracer(VertxTracerFactory)}
   * and {@link OpenTracingTracer#OpenTracingTracer(boolean, Tracer)}.
   */
  @Deprecated
  public OpenTracingOptions(Tracer tracer) {
    this.tracer = tracer;
    this.setFactory(OpenTracingTracerFactory.INSTANCE);
  }

  public OpenTracingOptions(OpenTracingOptions other) {
    tracer = other.tracer;
    this.setFactory(OpenTracingTracerFactory.INSTANCE);
  }

  public OpenTracingOptions(JsonObject json) {
    super(json);
    this.setFactory(OpenTracingTracerFactory.INSTANCE);
  }

  @Override
  public OpenTracingOptions copy() {
    return new OpenTracingOptions(this);
  }

  // Visible for testing
  Tracer getTracer() {
    return tracer;
  }

  io.vertx.core.spi.tracing.VertxTracer<?, ?> buildTracer() {
    if (tracer != null) {
      return new OpenTracingTracer(false, tracer);
    } else {
      return new OpenTracingTracer(true, OpenTracingTracer.createDefaultTracer());
    }
  }
}
