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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.vertx.core.Context;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;

import java.util.Map.Entry;
import java.util.function.BiConsumer;

import static io.vertx.tracing.opentelemetry.VertxContextStorageProvider.ACTIVE_CONTEXT;

class OpenTelemetryTracer implements VertxTracer<Span, Span> {

  private static final TextMapGetter<Iterable<Entry<String, String>>> getter = new HeadersPropagatorGetter();
  private static final TextMapSetter<BiConsumer<String, String>> setter = new HeadersPropagatorSetter();

  private final Tracer tracer;
  private final ContextPropagators propagators;

  OpenTelemetryTracer(final OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer("io.vertx");
    this.propagators = openTelemetry.getPropagators();
  }

  @Override
  public <R> Span receiveRequest(
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

    io.opentelemetry.context.Context tracingContext = propagators.getTextMapPropagator().extract(io.opentelemetry.context.Context.root(), headers, getter);

    // If no span, and policy is PROPAGATE, then don't create the span
    if (Span.fromContextOrNull(tracingContext) == null && TracingPolicy.PROPAGATE.equals(policy)) {
      return null;
    }

    final Span span = reportTagsAndStart(tracer
      .spanBuilder(operation)
      .setParent(tracingContext)
      .setSpanKind(SpanKind.RPC.equals(kind) ? io.opentelemetry.api.trace.SpanKind.SERVER : io.opentelemetry.api.trace.SpanKind.CONSUMER), request, tagExtractor);

    VertxContextStorageProvider.VertxContextStorage.INSTANCE.attach(context, tracingContext.with(span));

    return span;
  }

  @Override
  public <R> void sendResponse(
    final Context context,
    final R response,
    final Span span,
    final Throwable failure,
    final TagExtractor<R> tagExtractor) {
    if (span != null) {
      context.remove(ACTIVE_CONTEXT);
      end(span, response, tagExtractor, failure);
    }
  }

  private static <R> void end(Span span, R response, TagExtractor<R> tagExtractor, Throwable failure) {
    if (failure != null) {
      span.recordException(failure);
    }

    if (response != null) {
      tagExtractor.extractTo(response, span::setAttribute);
    }

    span.end();
  }

  @Override
  public <R> Span sendRequest(
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

    io.opentelemetry.context.Context tracingContext = context.getLocal(ACTIVE_CONTEXT);

    if (tracingContext == null && !TracingPolicy.ALWAYS.equals(policy)) {
      return null;
    }

    if (tracingContext == null) {
      tracingContext = io.opentelemetry.context.Context.root();
    }

    final Span span = reportTagsAndStart(tracer.spanBuilder(operation)
        .setParent(tracingContext)
        .setSpanKind(SpanKind.RPC.equals(kind) ? io.opentelemetry.api.trace.SpanKind.CLIENT : io.opentelemetry.api.trace.SpanKind.PRODUCER)
      , request, tagExtractor);

    tracingContext = tracingContext.with(span);
    propagators.getTextMapPropagator().inject(tracingContext, headers, setter);

    return span;
  }

  @Override
  public <R> void receiveResponse(
    final Context context,
    final R response,
    final Span span,
    final Throwable failure,
    final TagExtractor<R> tagExtractor) {
    if (span != null) {
      end(span, response, tagExtractor, failure);
    }
  }

  // tags need to be set before start, otherwise any sampler registered won't have access to it
  private <T> Span reportTagsAndStart(SpanBuilder span, T obj, TagExtractor<T> tagExtractor) {
    int len = tagExtractor.len(obj);
    for (int idx = 0; idx < len; idx++) {
      span.setAttribute(tagExtractor.name(obj, idx), tagExtractor.value(obj, idx));
    }
    return span.startSpan();
  }

}
