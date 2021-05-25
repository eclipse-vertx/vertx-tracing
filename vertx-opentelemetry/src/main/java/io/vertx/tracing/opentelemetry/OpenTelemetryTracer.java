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
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
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

class OpenTelemetryTracer implements VertxTracer<Scope, Scope> {

  private static final TextMapGetter<Iterable<Entry<String, String>>> getter = new HeadersPropagatorGetter();
  private static final TextMapSetter<BiConsumer<String, String>> setter = new HeadersPropagatorSetter();

  private final Tracer tracer;
  private final ContextPropagators propagators;

  OpenTelemetryTracer(final OpenTelemetry openTelemetry) {
    this.tracer = openTelemetry.getTracer("io.vertx");
    this.propagators = openTelemetry.getPropagators();
  }

  @Override
  public <R> Scope receiveRequest(
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

    io.opentelemetry.context.Context parentContext = context.getLocal(ACTIVE_CONTEXT);
    if (parentContext == null) {
      parentContext = io.opentelemetry.context.Context.root();
    }
    final io.opentelemetry.context.Context tracingContext = propagators.getTextMapPropagator().extract(parentContext, headers, getter);

    // OpenTelemetry SDK's Context is immutable, therefore if the extracted context is the same as the parent context
    // there is no tracing data to propagate downstream and we can return null.
    if (tracingContext == parentContext && TracingPolicy.PROPAGATE.equals(policy)) {
      return null;
    }

    final Span span = tracer
      .spanBuilder(operation)
      .setParent(tracingContext)
      .setSpanKind(SpanKind.RPC.equals(kind) ? io.opentelemetry.api.trace.SpanKind.SERVER : io.opentelemetry.api.trace.SpanKind.CONSUMER)
      .startSpan();

    tagExtractor.extractTo(request, span::setAttribute);

    return VertxContextStorageProvider.VertxContextStorage.INSTANCE.attach(context, tracingContext.with(span));
  }

  @Override
  public <R> void sendResponse(
    final Context context,
    final R response,
    final Scope scope,
    final Throwable failure,
    final TagExtractor<R> tagExtractor) {

    if (scope == null) {
      return;
    }

    Span span = Span.fromContext(context.getLocal(ACTIVE_CONTEXT));

    if (failure != null) {
      span.recordException(failure);
    }

    if (response != null) {
      tagExtractor.extractTo(response, span::setAttribute);
    }

    span.end();
    scope.close();
  }

  @Override
  public <R> Scope sendRequest(
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

    final io.opentelemetry.api.trace.SpanKind spanKind = SpanKind.RPC.equals(kind) ? io.opentelemetry.api.trace.SpanKind.CLIENT : io.opentelemetry.api.trace.SpanKind.PRODUCER;

    io.opentelemetry.context.Context tracingContext = context.getLocal(ACTIVE_CONTEXT);

    if (tracingContext == null && !TracingPolicy.ALWAYS.equals(policy)) {
      return null;
    }

    if (tracingContext == null) {
      tracingContext = io.opentelemetry.context.Context.root();
    }

    final Span span = tracer.spanBuilder(operation)
      .setParent(tracingContext)
      .setSpanKind(spanKind)
      .startSpan();

    tagExtractor.extractTo(request, span::setAttribute);

    tracingContext = tracingContext.with(span);
    propagators.getTextMapPropagator().inject(tracingContext, headers, setter);

    return VertxContextStorageProvider.VertxContextStorage.INSTANCE.attach(context, tracingContext);
  }

  @Override
  public <R> void receiveResponse(
    final Context context,
    final R response,
    final Scope scope,
    final Throwable failure,
    final TagExtractor<R> tagExtractor) {

    if (scope == null) {
      return;
    }

    Span span = Span.fromContext(context.getLocal(ACTIVE_CONTEXT));

    if (failure != null) {
      span.recordException(failure);
    }

    if (response != null) {
      tagExtractor.extractTo(response, span::setAttribute);
    }

    span.end();
    scope.close();
  }

}
