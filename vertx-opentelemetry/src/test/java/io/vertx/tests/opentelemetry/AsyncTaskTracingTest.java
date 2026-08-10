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

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.junit5.VertxExtension;
import io.vertx.tracing.opentelemetry.OpenTelemetryTracingFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tasks scheduled from a handler keep running on the request or message scoped Vert.x context after the
 * server or consumer span has ended: spans they create must remain part of the trace.
 */
@ExtendWith(VertxExtension.class)
public class AsyncTaskTracingTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  private Vertx vertx;
  private HttpServer server;
  private HttpClient client;

  @BeforeEach
  public void setUp() throws Exception {
    vertx = Vertx
      .builder()
      .withTracer(new OpenTelemetryTracingFactory(otelTesting.getOpenTelemetry()))
      .build();
    server = vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.IGNORE))
      .requestHandler(req -> req.response().end())
      .listen(0)
      .await(20, TimeUnit.SECONDS);
    client = vertx.createHttpClient();
  }

  @AfterEach
  public void tearDown() throws Exception {
    vertx.close().await(20, TimeUnit.SECONDS);
  }

  private void httpGet() {
    client.request(new RequestOptions().setMethod(HttpMethod.GET).setHost("localhost").setPort(server.actualPort()))
      .compose(HttpClientRequest::send)
      .compose(HttpClientResponse::body);
  }

  @Test
  public void clientCallAfterPublishConsumerHandlerReturnsJoinsTheTrace() {
    vertx.eventBus().consumer("the-address", msg ->
      // runs after the handler has returned and the consumer span has ended
      vertx.runOnContext(v -> httpGet())
    );

    vertx.getOrCreateContext().runOnContext(v ->
      vertx.eventBus().publish("the-address", "ping", new DeliveryOptions().setTracingPolicy(TracingPolicy.ALWAYS))
    );

    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      List<SpanData> spans = otelTesting.getSpans();
      SpanData consumerSpan = spans.stream()
        .filter(span -> span.getName().equals("publish") && span.getKind() == SpanKind.SERVER)
        .findFirst()
        .orElse(null);
      assertThat(consumerSpan).isNotNull();
      List<SpanData> clientSpans = spans.stream()
        .filter(span -> span.getKind() == SpanKind.CLIENT && span.getName().equals("GET"))
        .collect(Collectors.toList());
      assertThat(clientSpans).isNotEmpty();
      // the default HTTP client policy is PROPAGATE: the span only exists if the trace was propagated
      assertThat(clientSpans).allSatisfy(clientSpan ->
        assertThat(clientSpan.getTraceId()).isEqualTo(consumerSpan.getTraceId()));
    });
  }

  @Test
  public void clientCallAfterHttpResponseEndsJoinsTheTrace() throws Exception {
    HttpServer tracedServer = vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS))
      .requestHandler(req ->
        req.response().end().onComplete(v ->
          // runs after the response was sent and the server span has ended
          httpGet()
        )
      )
      .listen(0)
      .await(20, TimeUnit.SECONDS);

    client.request(new RequestOptions().setMethod(HttpMethod.GET).setHost("localhost").setPort(tracedServer.actualPort()))
      .compose(HttpClientRequest::send)
      .compose(HttpClientResponse::body)
      .await(20, TimeUnit.SECONDS);

    await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
      List<SpanData> spans = otelTesting.getSpans();
      SpanData serverSpan = spans.stream()
        .filter(span -> span.getKind() == SpanKind.SERVER && span.getName().equals("GET"))
        .findFirst()
        .orElse(null);
      assertThat(serverSpan).isNotNull();
      List<SpanData> clientSpans = spans.stream()
        .filter(span -> span.getKind() == SpanKind.CLIENT && span.getName().equals("GET"))
        .collect(Collectors.toList());
      assertThat(clientSpans).isNotEmpty();
      assertThat(clientSpans).allSatisfy(clientSpan ->
        assertThat(clientSpan.getTraceId()).isEqualTo(serverSpan.getTraceId()));
    });
  }
}
