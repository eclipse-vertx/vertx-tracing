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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
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
import io.vertx.tracing.opentelemetry.SpanCustomizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ExtendWith(VertxExtension.class)
public class SpanCustomizerTest {

  private static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("tenant.id");

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @SuppressWarnings("unchecked")
  private static VertxTracer<Operation, Operation> tracer(OpenTelemetry openTelemetry, SpanCustomizer customizer) {
    return (VertxTracer<Operation, Operation>) new OpenTelemetryTracingFactory(openTelemetry)
      .withSpanCustomizer(customizer)
      .tracer(new OpenTelemetryOptions());
  }

  @Test
  public void customAttributesAreVisibleToTheSampler(Vertx vertx) {
    AtomicReference<String> sampledValue = new AtomicReference<>();
    Sampler sampler = new Sampler() {
      @Override
      public SamplingResult shouldSample(Context parentContext, String traceId, String name, io.opentelemetry.api.trace.SpanKind spanKind, Attributes attributes, List<LinkData> parentLinks) {
        sampledValue.set(attributes.get(TENANT_ID));
        return SamplingResult.recordAndSample();
      }

      @Override
      public String getDescription() {
        return "test-sampler";
      }
    };
    OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
      .setTracerProvider(SdkTracerProvider.builder().setSampler(sampler).build())
      .build();
    VertxTracer<Operation, Operation> tracer = tracer(sdk, (spanBuilder, request) -> spanBuilder.setAttribute(TENANT_ID, (String) request));

    Operation operation = tracer.receiveRequest(
      vertx.getOrCreateContext(),
      SpanKind.RPC,
      TracingPolicy.ALWAYS,
      "the-tenant",
      "GET",
      Collections.emptyList(),
      TagExtractor.empty()
    );

    assertThat(operation).isNotNull();
    assertThat(sampledValue.get()).isEqualTo("the-tenant");
  }

  @Test
  public void customizerIsAppliedToClientSpans(Vertx vertx) {
    VertxTracer<Operation, Operation> tracer = tracer(otelTesting.getOpenTelemetry(), (spanBuilder, request) -> spanBuilder.setAttribute(TENANT_ID, "42"));

    Operation operation = tracer.sendRequest(
      vertx.getOrCreateContext(),
      SpanKind.RPC,
      TracingPolicy.ALWAYS,
      "the-request",
      "GET",
      (k, v) -> {
      },
      TagExtractor.empty()
    );

    assertThat(operation).isNotNull();
    tracer.receiveResponse(vertx.getOrCreateContext(), null, operation, null, TagExtractor.empty());

    assertThat(otelTesting.getSpans())
      .anySatisfy(span -> assertThat(span.getAttributes().get(TENANT_ID)).isEqualTo("42"));
  }

  @Test
  public void customizerReceivesTheHttpServerRequest() throws Exception {
    Vertx vertx = Vertx
      .builder()
      .withTracer(new OpenTelemetryTracingFactory(otelTesting.getOpenTelemetry())
        .withSpanCustomizer((spanBuilder, request) -> {
          if (request instanceof HttpRequest) {
            spanBuilder.setAttribute(TENANT_ID, ((HttpRequest) request).headers().get("x-tenant"));
          }
        }))
      .build();
    try {
      HttpServer server = vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS))
        .requestHandler(req -> req.response().end())
        .listen(0)
        .await(20, TimeUnit.SECONDS);

      URL url = new URL("http://localhost:" + server.actualPort());
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestProperty("x-tenant", "acme");
      assertThat(con.getResponseCode()).isEqualTo(200);

      await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
        assertThat(otelTesting.getSpans())
          .anySatisfy(span -> assertThat(span.getAttributes().get(TENANT_ID)).isEqualTo("acme")));
    } finally {
      vertx.close().await(20, TimeUnit.SECONDS);
    }
  }
}
