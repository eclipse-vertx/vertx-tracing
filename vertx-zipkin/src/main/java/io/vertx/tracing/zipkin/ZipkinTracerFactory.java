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
import brave.Tracing;
import brave.http.HttpTracing;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.context.storage.ContextLocal;
import io.vertx.core.tracing.TracingOptions;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;

public class ZipkinTracerFactory implements VertxTracerFactory {

  static final ContextLocal<Span> ACTIVE_SPAN = ContextLocal.registerLocal(Span.class);
  static final ContextLocal<TraceContext> ACTIVE_CONTEXT = ContextLocal.registerLocal(TraceContext.class);
  static final ContextLocal<HttpServerRequest> ACTIVE_REQUEST = ContextLocal.registerLocal(HttpServerRequest.class);

  private final Sampler sampler;
  private final HttpTracing httpTracing;

  public ZipkinTracerFactory() {
    this(null, null);
  }

  public ZipkinTracerFactory(Sampler sampler) {
    this(sampler, null);
  }

  public ZipkinTracerFactory(HttpTracing httpTracing) {
    this(null, httpTracing);
  }

  public ZipkinTracerFactory(Tracing tracing) {
    this(null, HttpTracing.newBuilder(tracing).build());
  }

  private ZipkinTracerFactory(Sampler sampler, HttpTracing httpTracing) {
    if (httpTracing != null) {
      this.httpTracing = httpTracing;
      this.sampler = null;
    } else {
      this.httpTracing = null;
      this.sampler = sampler != null ? sampler : Sampler.ALWAYS_SAMPLE;
    }
  }

  @Override
  public ZipkinTracer tracer(TracingOptions options) {
    if (httpTracing != null) {
      return new ZipkinTracer(false, httpTracing, null);
    }
    ZipkinTracingOptions zipkinOptions;
    if (options instanceof ZipkinTracingOptions) {
      zipkinOptions = (ZipkinTracingOptions) options;
    } else {
      zipkinOptions = new ZipkinTracingOptions(options.toJson());
    }
    HttpSenderOptions senderOptions = zipkinOptions.getSenderOptions();
    if (senderOptions != null) {
      String localServiceName = zipkinOptions.getServiceName();
      VertxSender sender = new VertxSender(senderOptions);
      assert sampler != null : "sampler shouldn't be null when httpTracing is null";
      Tracing tracing = Tracing
        .newBuilder()
        .supportsJoin(zipkinOptions.isSupportsJoin())
        .localServiceName(localServiceName)
        .addSpanHandler(AsyncZipkinSpanHandler.create(sender))
        .sampler(sampler)
        .build();
      return new ZipkinTracer(true, tracing, sender);
    } else {
      return null;
    }
  }

  @Override
  public ZipkinTracingOptions newOptions() {
    return new ZipkinTracingOptions();
  }

  @Override
  public ZipkinTracingOptions newOptions(JsonObject jsonObject) {
    return new ZipkinTracingOptions(jsonObject);
  }
}
