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
package io.vertx.tracing.zipkin;

import brave.Span;
import brave.propagation.TraceContext;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.core.tracing.TracingOptions;

public class ZipkinTracerFactory implements VertxTracerFactory {

  static final ContextLocal<Span> ACTIVE_SPAN = ContextLocal.registerLocal(Span.class);
  static final ContextLocal<TraceContext> ACTIVE_CONTEXT = ContextLocal.registerLocal(TraceContext.class);
  static final ContextLocal<HttpServerRequest> ACTIVE_REQUEST = ContextLocal.registerLocal(HttpServerRequest.class);

  @Override
  public ZipkinTracer tracer(TracingOptions options) {
    ZipkinTracingOptions zipkinOptions;
    if (options instanceof ZipkinTracingOptions) {
      zipkinOptions = (ZipkinTracingOptions) options;
    } else {
      zipkinOptions = new ZipkinTracingOptions(options.toJson());
    }
    return zipkinOptions.buildTracer();
  }

  @Override
  public TracingOptions newOptions() {
    return new ZipkinTracingOptions();
  }

  @Override
  public TracingOptions newOptions(JsonObject jsonObject) {
    return new ZipkinTracingOptions(jsonObject);
  }
}
