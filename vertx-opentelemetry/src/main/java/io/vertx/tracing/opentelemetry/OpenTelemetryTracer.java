/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tracing.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.vertx.core.Context;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.tracing.opentelemetry.VertxContextStorageProvider.VertxContextStorage;

import java.util.Map.Entry;
import java.util.function.BiConsumer;

import static io.vertx.tracing.opentelemetry.OpenTelemetryTracingFactory.ACTIVE_CONTEXT;

class OpenTelemetryTracer implements VertxTracer<Operation, Operation> {

  private static final TextMapGetter<Iterable<Entry<String, String>>> getter = new HeadersPropagatorGetter();
  private static final TextMapSetter<BiConsumer<String, String>> setter = new HeadersPropagatorSetter();

  private final Tracer tracer;
  private final ContextPropagators propagators;

  OpenTelemetryTracer(final OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer("io.vertx");
    this.propagators = openTelemetry.getPropagators();
  }

  @Override
  public <R> Operation receiveRequest(
    final Context context,
    final SpanKind kind,
    final TracingPolicy policy,
    final R request,
    final String operation,
    final Iterable<Entry<String, String>> headers,
    final TagExtractor<R> tagExtractor) {

    if (TracingPolicy.IGNORE.equals(policy)) {
      return null;
    }

    ContextInternal ctx = (ContextInternal) context;
    io.opentelemetry.context.Context otelCtx = ctx.getLocal(ACTIVE_CONTEXT);
    if (otelCtx == null) {
      otelCtx = io.opentelemetry.context.Context.root();
    }

    otelCtx = propagators.getTextMapPropagator().extract(otelCtx, headers, getter);

    // If no span, and policy is PROPAGATE, then don't create the span
    if (Span.fromContextOrNull(otelCtx) == null && TracingPolicy.PROPAGATE.equals(policy)) {
      return null;
    }

    io.opentelemetry.api.trace.SpanKind spanKind = SpanKind.RPC.equals(kind) ? io.opentelemetry.api.trace.SpanKind.SERVER : io.opentelemetry.api.trace.SpanKind.CONSUMER;

    SpanBuilder spanBuilder = tracer
      .spanBuilder(operation)
      .setParent(otelCtx)
      .setSpanKind(spanKind);

    Span span = reportTagsAndStart(spanBuilder, request, tagExtractor, false);
    Scope scope = VertxContextStorage.INSTANCE.attach(ctx, span.storeInContext(otelCtx));

    return new Operation(span, scope);
  }

  @Override
  public <R> void sendResponse(
    final Context context,
    final R response,
    final Operation operation,
    final Throwable failure,
    final TagExtractor<R> tagExtractor) {
    if (operation != null) {
      end(operation, response, tagExtractor, failure, false);
    }
  }

  private static <R> void end(Operation operation, R response, TagExtractor<R> tagExtractor, Throwable failure, boolean client) {
    Span span = operation.span();
    try {
      if (failure != null) {
        span.recordException(failure);
      }
      if (response != null) {
        Attributes attributes = processTags(response, tagExtractor, client);
        span.setAllAttributes(attributes);
      }
      span.end();
    } finally {
      operation.scope().close();
    }
  }

  @Override
  public <R> Operation sendRequest(
    final Context context,
    final SpanKind kind,
    final TracingPolicy policy,
    final R request,
    final String operation,
    final BiConsumer<String, String> headers,
    final TagExtractor<R> tagExtractor) {

    if (TracingPolicy.IGNORE.equals(policy) || request == null) {
      return null;
    }

    io.opentelemetry.context.Context otelCtx = ((ContextInternal) context).getLocal(ACTIVE_CONTEXT);

    if (otelCtx == null) {
      if (!TracingPolicy.ALWAYS.equals(policy)) {
        return null;
      }
      otelCtx = io.opentelemetry.context.Context.root();
    }

    io.opentelemetry.api.trace.SpanKind spanKind = SpanKind.RPC.equals(kind) ? io.opentelemetry.api.trace.SpanKind.CLIENT : io.opentelemetry.api.trace.SpanKind.PRODUCER;

    SpanBuilder spanBuilder = tracer.spanBuilder(operation)
      .setParent(otelCtx)
      .setSpanKind(spanKind);

    Span span = reportTagsAndStart(spanBuilder, request, tagExtractor, true);

    otelCtx = otelCtx.with(span);
    propagators.getTextMapPropagator().inject(otelCtx, headers, setter);

    return new Operation(span, Scope.noop());
  }

  @Override
  public <R> void receiveResponse(
    final Context context,
    final R response,
    final Operation operation,
    final Throwable failure,
    final TagExtractor<R> tagExtractor) {
    if (operation != null) {
      end(operation, response, tagExtractor, failure, true);
    }
  }

  // tags need to be set before start, otherwise any sampler registered won't have access to it
  private <T> Span reportTagsAndStart(SpanBuilder span, T obj, TagExtractor<T> tagExtractor, boolean client) {
    Attributes attributes = processTags(obj, tagExtractor, client);
    span.setAllAttributes(attributes);
    return span.startSpan();
  }

  private static <T> Attributes processTags(T obj, TagExtractor<T> tagExtractor, boolean client) {
    AttributesBuilder builder = Attributes.builder();
    int len = tagExtractor.len(obj);
    for (int idx = 0; idx < len; idx++) {
      builder.put(tagExtractor.name(obj, idx), tagExtractor.value(obj, idx));
    }
    return builder.build();
  }
}
