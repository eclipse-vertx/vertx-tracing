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

import brave.Tracing;
import brave.http.HttpTracing;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.tracing.TracingOptions;
import zipkin2.reporter.Sender;

public class ZipkinTracerFactory implements VertxTracerFactory {

  static final ZipkinTracerFactory INSTANCE = new ZipkinTracerFactory();

  private final HttpTracing httpTracing;
  private final Sender sender;

  public ZipkinTracerFactory() {
    this.httpTracing = null;
    this.sender = null;
  }

  public ZipkinTracerFactory(Tracing tracing) {
    this(tracing, null);
  }

  public ZipkinTracerFactory(Tracing tracing, Sender sender) {
    this.httpTracing = HttpTracing.newBuilder(tracing).build();
    this.sender = sender;
  }

  public ZipkinTracerFactory(HttpTracing httpTracing) {
    this(httpTracing, null);
  }

  public ZipkinTracerFactory(HttpTracing httpTracing, Sender sender) {
    this.httpTracing = httpTracing;
    this.sender = sender;
  }

  @Override
  public ZipkinTracer tracer(TracingOptions options) {
    if (httpTracing != null) {
      return new ZipkinTracer(false, httpTracing, sender);
    } else {
      ZipkinTracingOptions zipkinOptions;
      if (options instanceof ZipkinTracingOptions) {
        zipkinOptions = (ZipkinTracingOptions) options;
      } else {
        zipkinOptions = new ZipkinTracingOptions(options.toJson());
      }
      return zipkinOptions.buildTracer();
    }
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
