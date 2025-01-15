/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
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
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.junit5.VertxExtension;
import io.vertx.tracing.opentelemetry.VertxContextStorageProvider.VertxContextStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.Serializable;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
public class OpenTelemetryTracingFactoryTest {

  @Test
  public void receiveRequestShouldNotReturnSpanIfPolicyIsIgnore(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    final Operation operation = tracer.receiveRequest(
      vertx.getOrCreateContext(),
      SpanKind.MESSAGING,
      TracingPolicy.IGNORE,
      null,
      "",
      Collections.emptyList(),
      TagExtractor.empty()
    );

    assertThat(operation).isNull();
  }

  @Test
  public void receiveRequestShouldNotReturnSpanIfPolicyIsPropagateAndPreviousContextIsNotPresent(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    final Operation operation = tracer.receiveRequest(
      vertx.getOrCreateContext(),
      SpanKind.MESSAGING,
      TracingPolicy.PROPAGATE,
      null,
      "",
      Collections.emptyList(),
      TagExtractor.empty()
    );

    assertThat(operation).isNull();
  }

  @Test
  public void receiveRequestShouldReturnSpanIfPolicyIsPropagateAndPreviousContextIsPresent(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(
      OpenTelemetry.propagating(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
    ).buildTracer();

    final Iterable<Map.Entry<String, String>> headers = Collections.singletonList(
      new SimpleImmutableEntry<>("traceparent", "00-83ebbd06a32c2eaa8d5bf4b060d7cbfa-140cd1a04ab7be4b-01")
    );

    final io.vertx.core.Context ctx = vertx.getOrCreateContext();
    final Operation operation = tracer.receiveRequest(
      ctx,
      SpanKind.MESSAGING,
      TracingPolicy.PROPAGATE,
      null,
      "",
      headers,
      TagExtractor.empty()
    );

    assertThat(operation).isNotNull();

    final io.opentelemetry.context.Context tracingContext = VertxContextStorage.INSTANCE.current();
    assertThat(tracingContext).isNotNull();
  }

  @Test
  public void receiveRequestShouldReturnAParentedSpanIfPolicyIsPropagateAndTheOtelContextHasAnOngoingSpan(final Vertx vertx) throws ExecutionException, InterruptedException {
    final OpenTelemetry openTelemetry = OpenTelemetry.propagating(
      ContextPropagators.create(W3CTraceContextPropagator.getInstance())
    );

    final Tracer otelTracer = openTelemetry.getTracer("example-lib");

    final Span parentSpan = otelTracer.spanBuilder("example-span")
      .startSpan();

    CompletableFuture<Operation> futureOperation = new CompletableFuture<>();

    vertx.runOnContext(unused -> {
      parentSpan.makeCurrent();

      VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(openTelemetry).buildTracer();

      final Operation operation = tracer.receiveRequest(
        vertx.getOrCreateContext(),
        SpanKind.MESSAGING,
        TracingPolicy.PROPAGATE,
        null,
        "",
        Collections.emptyList(),
        TagExtractor.empty()
      );

      parentSpan.end();

      futureOperation.complete(operation);
    });

    Operation operation = futureOperation.get();

    assertThat(operation).isNotNull();
    assertThat(operation.span().getSpanContext().getTraceId())
      .isEqualTo(parentSpan.getSpanContext().getTraceId());
  }

  @Test
  public void sendResponseEndsSpan(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    Span span = mock(Span.class);
    doNothing().when(span).end();
    Scope scope = mock(Scope.class);
    Operation operation = new Operation(span, scope);

    tracer.sendResponse(
      vertx.getOrCreateContext(),
      mock(Serializable.class),
      operation,
      mock(Exception.class),
      TagExtractor.empty()
    );

    verify(span, times(1)).end();
  }

  @Test
  public void sendResponseShouldNotThrowExceptionWhenSpanIsNull(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    assertThatNoException().isThrownBy(() -> tracer.sendResponse(
      vertx.getOrCreateContext(),
      mock(Serializable.class),
      null,
      mock(Exception.class),
      TagExtractor.empty()
    ));
  }

  @Test
  public void sendRequestShouldNotReturnSpanIfRequestIsNull(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    final Context ctx = vertx.getOrCreateContext();
    VertxContextStorage.INSTANCE.attach(io.opentelemetry.context.Context.current());

    final Operation operation = tracer.sendRequest(
      ctx,
      SpanKind.MESSAGING,
      TracingPolicy.PROPAGATE,
      null,
      "",
      (k, v) -> {
      },
      TagExtractor.empty()
    );

    assertThat(operation).isNull();
  }

  @Test
  public void sendRequestShouldNotReturnSpanIfPolicyIsIgnore(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    final Context ctx = vertx.getOrCreateContext();
    VertxContextStorage.INSTANCE.attach(io.opentelemetry.context.Context.current());

    final Operation operation = tracer.sendRequest(
      ctx,
      SpanKind.MESSAGING,
      TracingPolicy.IGNORE,
      mock(Serializable.class),
      "",
      (k, v) -> {
      },
      TagExtractor.empty()
    );

    assertThat(operation).isNull();
  }


  @Test
  public void sendRequestShouldNotReturnSpanIfPolicyIsPropagateAndPreviousContextIsNotPresent(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    final Operation operation = tracer.sendRequest(
      vertx.getOrCreateContext(),
      SpanKind.MESSAGING,
      TracingPolicy.PROPAGATE,
      null,
      "",
      (k, v) -> {
      },
      TagExtractor.empty()
    );

    assertThat(operation).isNull();
  }

  @Test
  public void sendRequestShouldReturnSpanIfPolicyIsPropagateAndPreviousContextIsPresent(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    final ContextInternal ctx = (ContextInternal) vertx.getOrCreateContext();
    ctx.putLocal(VertxContextStorageProvider.ACTIVE_CONTEXT, io.opentelemetry.context.Context.current());

    final Operation operation = tracer.sendRequest(
      ctx,
      SpanKind.MESSAGING,
      TracingPolicy.PROPAGATE,
      mock(Serializable.class),
      "",
      (k, v) -> {
      },
      TagExtractor.empty()
    );

    assertThat(operation).isNotNull();
  }

  @Test
  public void sendRequestShouldReturnSpanIfPolicyIsAlwaysAndPreviousContextIsNotPresent(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    final Context ctx = vertx.getOrCreateContext();

    final Operation operation = tracer.sendRequest(
      ctx,
      SpanKind.MESSAGING,
      TracingPolicy.ALWAYS,
      mock(Serializable.class),
      "",
      (k, v) -> {
      },
      TagExtractor.empty()
    );

    assertThat(operation).isNotNull();
  }

  @Test
  public void receiveResponseEndsSpan(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    Span span = mock(Span.class);
    doNothing().when(span).end();
    Scope scope = mock(Scope.class);
    Operation operation = new Operation(span, scope);

    tracer.receiveResponse(
      vertx.getOrCreateContext(),
      mock(Serializable.class),
      operation,
      mock(Exception.class),
      TagExtractor.empty()
    );

    verify(span, times(1)).end();
  }

  @Test
  public void receiveResponseShouldNotThrowExceptionWhenSpanIsNull(final Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = new OpenTelemetryOptions(OpenTelemetry.noop()).buildTracer();

    assertThatNoException().isThrownBy(() -> tracer.receiveResponse(
      vertx.getOrCreateContext(),
      mock(Serializable.class),
      null,
      mock(Exception.class),
      TagExtractor.empty()
    ));
  }
}
