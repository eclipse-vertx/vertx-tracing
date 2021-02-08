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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


@ExtendWith(VertxExtension.class)
public class OpenTelemetryIntegrationTest {

  @RegisterExtension
  final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();
  private Tracer tracer;
  private Vertx vertx;
  private TextMapPropagator textMapPropagator;

  private final static TextMapPropagator.Setter<HttpURLConnection> setter = HttpURLConnection::setRequestProperty;

  @BeforeEach
  public void setUp() throws Exception {
    tracer = otelTesting.getOpenTelemetry().getTracer("testing");
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new OpenTelemetryOptions(tracer)));
    textMapPropagator = otelTesting.getOpenTelemetry().getPropagators().getTextMapPropagator();
  }

  @AfterEach
  public void tearDown(VertxTestContext context) throws Exception {
    vertx.close(context.succeedingThenComplete());
  }

  private static Stream<Arguments> testTracingPolicyArgs() {
    return Stream.of(TracingPolicy.IGNORE, TracingPolicy.PROPAGATE, TracingPolicy.ALWAYS)
      .flatMap(policy -> Stream.of(
        Arguments.of(policy, false),
        Arguments.of(policy, true)
      ));
  }

  @ParameterizedTest
  @MethodSource("testTracingPolicyArgs")
  public void testHttpServerRequestWithPolicy(TracingPolicy policy, boolean createTrace, VertxTestContext ctx) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    final boolean expectTrace = (policy == TracingPolicy.PROPAGATE && createTrace) || policy == TracingPolicy.ALWAYS;

    ctx.assertComplete(
      vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(policy)).requestHandler(req -> {
        ctx.verify(() -> {
          if (expectTrace) {
            assertThat(OpenTelemetryUtil.getSpan())
              .isNotNull();
          } else {
            assertThat(OpenTelemetryUtil.getSpan())
              .isNull();
          }
        });
        req.response().end();
      }).listen(8080).onSuccess(v -> latch.countDown())
    );

    latch.countDown();

    if (createTrace) {
      sendRequestWithTrace();
    } else {
      sendRequest();
    }

    if (expectTrace) {
      otelTesting.assertTraces()
        .size()
        .isGreaterThanOrEqualTo(1);

      otelTesting.assertTraces()
        .anySatisfy(spans -> assertThat(spans).anySatisfy(spanData -> {
          assertThat(spanData.getAttributes().get(SemanticAttributes.HTTP_URL))
            .startsWith("http://localhost:8080");
        }));
    }
    if (createTrace) {
      otelTesting.assertTraces()
        .anySatisfy(spans -> assertThat(spans).anySatisfy(spanData -> {
          assertThat(spanData.getAttributes().get(AttributeKey.stringKey("component")))
            .isEqualTo("vertx");
          assertThat(spanData.getAttributes().get(SemanticAttributes.HTTP_URL))
            .startsWith("http://localhost:8080");
        }));
    }
    ctx.completeNow();
  }

  @ParameterizedTest
  @MethodSource("testTracingPolicyArgs")
  public void testHttpClientRequestWithPolicy(TracingPolicy policy, boolean createTrace, VertxTestContext ctx) throws Exception {
    int expectedTrace = (createTrace ? 1 : 0) +
      (policy == TracingPolicy.PROPAGATE && createTrace ? 2 : 0) +
      (policy == TracingPolicy.ALWAYS ? 2 : 0);

    CountDownLatch latch = new CountDownLatch(2);
    HttpClient c = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(policy));

    // Proxy server
    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE)).requestHandler(req ->
      c.request(HttpMethod.GET, 8081, "localhost", "/", ctx.succeeding(clientReq ->
        clientReq.send(ctx.succeeding(clientResp ->
          req.response().end()
        ))
      ))
    ).listen(8080, ctx.succeeding(v -> latch.countDown()));

    // End server
    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE))
      .requestHandler(req -> req.response().end())
      .listen(8081, ctx.succeeding(v -> latch.countDown()));

    latch.await();

    if (createTrace) {
      sendRequestWithTrace();
    } else {
      sendRequest();
    }

    if (expectedTrace > 0) {
      await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
        otelTesting.assertTraces()
          .anySatisfy(spanData ->
            assertThat(spanData)
              .size()
              .isGreaterThanOrEqualTo(expectedTrace)
          );

        assertThat(otelTesting.getSpans())
          .anySatisfy(spanData -> {
            assertThat(spanData.getAttributes().get(SemanticAttributes.HTTP_METHOD))
              .isEqualTo("GET");
            assertThat(spanData.getAttributes().get(SemanticAttributes.HTTP_URL))
              .startsWith(createTrace ? "http://localhost:8080" : "http://localhost:8081");
          });
      });
    } else {
      otelTesting.assertTraces()
        .hasSize(0);
    }
    ctx.completeNow();
  }

  private void sendRequest() throws IOException {
    URL url = new URL("http://localhost:8080");
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setRequestMethod("GET");
    assertThat(con.getResponseCode()).isEqualTo(200);
  }

  private void sendRequestWithTrace() throws IOException {
    URL url = new URL("http://localhost:8080");

    Span span = tracer.spanBuilder("/")
      .setSpanKind(Span.Kind.CLIENT)
      .setAttribute("component", "vertx")
      .startSpan();
    try (Scope scope = span.makeCurrent()) {
      span
        .setAttribute(SemanticAttributes.HTTP_METHOD, "GET")
        .setAttribute(SemanticAttributes.HTTP_URL, url.toString());

      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      textMapPropagator.inject(io.opentelemetry.context.Context.current(), con, setter);
      con.setRequestMethod("GET");

      assertThat(con.getResponseCode()).isEqualTo(200);
    } finally {
      span.end();
    }
  }

  @Test
  public void testEventBus(VertxTestContext ctx) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);

    // Ping pong
    vertx.eventBus().consumer("the-address", msg -> msg.reply("pong"));

    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE)).requestHandler(req ->
      vertx.eventBus().request("the-address", "ping", ctx.succeeding(resp ->
        req.response().end()
      ))
    ).listen(8080, ctx.succeeding(v -> latch.countDown()));

    latch.await();

    sendRequestWithTrace();

    otelTesting.assertTraces().anySatisfy(spanDataList ->
      assertThat(spanDataList)
        .anySatisfy(spanData ->
          assertThat(spanData.getAttributes().get(AttributeKey.stringKey("message_bus.destination")))
            .isEqualTo("the-address")
        )
    );

    ctx.completeNow();
  }
}
