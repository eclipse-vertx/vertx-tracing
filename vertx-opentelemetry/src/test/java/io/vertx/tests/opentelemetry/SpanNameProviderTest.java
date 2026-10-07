/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.junit5.VertxExtension;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import io.vertx.tracing.opentelemetry.OpenTelemetryTracingFactory;
import io.vertx.tracing.opentelemetry.Operation;
import io.vertx.tracing.opentelemetry.SpanNameProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(VertxExtension.class)
public class SpanNameProviderTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @SuppressWarnings("unchecked")
  private static VertxTracer<Operation, Operation> tracer(OpenTelemetry openTelemetry, SpanNameProvider provider) {
    return (VertxTracer<Operation, Operation>) new OpenTelemetryTracingFactory(openTelemetry)
      .withSpanNameProvider(provider)
      .tracer(new OpenTelemetryOptions());
  }

  @Test
  public void providerOverridesServerSpanName(Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = tracer(otelTesting.getOpenTelemetry(), (operation, request) -> operation + " " + request);

    Operation operation = tracer.receiveRequest(
      vertx.getOrCreateContext(),
      SpanKind.RPC,
      TracingPolicy.ALWAYS,
      "/the-route",
      "GET",
      Collections.emptyList(),
      TagExtractor.empty()
    );

    assertThat(operation).isNotNull();
    tracer.sendResponse(vertx.getOrCreateContext(), null, operation, null, TagExtractor.empty());

    assertThat(otelTesting.getSpans())
      .anySatisfy(span -> assertThat(span.getName()).isEqualTo("GET /the-route"));
  }

  @Test
  public void providerOverridesClientSpanName(Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = tracer(otelTesting.getOpenTelemetry(), (operation, request) -> operation + " " + request);

    Operation operation = tracer.sendRequest(
      vertx.getOrCreateContext(),
      SpanKind.RPC,
      TracingPolicy.ALWAYS,
      "/the-target",
      "GET",
      (k, v) -> {
      },
      TagExtractor.empty()
    );

    assertThat(operation).isNotNull();
    tracer.receiveResponse(vertx.getOrCreateContext(), null, operation, null, TagExtractor.empty());

    assertThat(otelTesting.getSpans())
      .anySatisfy(span -> assertThat(span.getName()).isEqualTo("GET /the-target"));
  }

  @Test
  public void operationIsUsedWhenProviderReturnsNull(Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = tracer(otelTesting.getOpenTelemetry(), (operation, request) -> null);

    Operation operation = tracer.receiveRequest(
      vertx.getOrCreateContext(),
      SpanKind.RPC,
      TracingPolicy.ALWAYS,
      "/the-route",
      "GET",
      Collections.emptyList(),
      TagExtractor.empty()
    );

    assertThat(operation).isNotNull();
    tracer.sendResponse(vertx.getOrCreateContext(), null, operation, null, TagExtractor.empty());

    assertThat(otelTesting.getSpans())
      .anySatisfy(span -> assertThat(span.getName()).isEqualTo("GET"));
  }

  @Test
  public void providerReceivesTheHttpServerRequest() throws Exception {
    Vertx vertx = Vertx
      .builder()
      .withTracer(new OpenTelemetryTracingFactory(otelTesting.getOpenTelemetry())
        .withSpanNameProvider((operation, request) -> {
          if (request instanceof HttpRequest) {
            return operation + " " + ((HttpRequest) request).uri();
          }
          return operation;
        }))
      .build();
    try {
      HttpServer server = vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS))
        .requestHandler(req -> req.response().end())
        .listen(0)
        .await(20, TimeUnit.SECONDS);

      URL url = new URL("http://localhost:" + server.actualPort() + "/users");
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      assertThat(con.getResponseCode()).isEqualTo(200);

      await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
        assertThat(otelTesting.getSpans())
          .anySatisfy(span -> assertThat(span.getName()).isEqualTo("GET /users")));
    } finally {
      vertx.close().await(20, TimeUnit.SECONDS);
    }
  }
}
