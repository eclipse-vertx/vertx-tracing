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
package io.vertx.tracing.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
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

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(VertxExtension.class)
public class OpenTelemetryIntegrationTest {

  @RegisterExtension
  final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();
  private Tracer tracer;
  private Vertx vertx;
  private TextMapPropagator textMapPropagator;

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

  @Test
  public void testHttpServerRequestIgnorePolicy1(VertxTestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.IGNORE), true, false);
  }

  @Test
  public void testHttpServerRequestIgnorePolicy2(VertxTestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.IGNORE), false, false);
  }

  @Test
  public void testHttpServerRequestPropagatePolicy1(VertxTestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE), true, true);
  }

  @Test
  public void testHttpServerRequestPropagatePolicy2(VertxTestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE), false, false);
  }

  @Test
  public void testHttpServerRequestSupportPolicy1(VertxTestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS), false, true);
  }

  @Test
  public void testHttpServerRequestSupportPolicy2(VertxTestContext ctx) throws Exception {
    testHttpServerRequestPolicy(ctx, new HttpServerOptions().setTracingPolicy(TracingPolicy.ALWAYS), true, true);
  }

  private void testHttpServerRequestPolicy(VertxTestContext ctx,
                                           HttpServerOptions options,
                                           boolean createTrace,
                                           boolean expectTrace) throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    ctx.assertComplete(
      vertx.createHttpServer(options).requestHandler(req -> {
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
      }).listen(8080)
    ).onSuccess(v -> latch.countDown());

    sendRequest(createTrace);
    if (expectTrace) {
      otelTesting.assertTraces()
        .size()
        .isGreaterThanOrEqualTo(1);

      List<SpanData> spans = otelTesting.getSpans();
      SpanData spanData = spans.get(0);
      assertThat(spanData.getAttributes().get(AttributeKey.stringKey("component")))
        .isEqualTo("vertx");
    }
    ctx.completeNow();
  }

  @Test
  public void testHttpClientRequestIgnorePolicy1(VertxTestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.IGNORE, true, 1);
  }

  @Test
  public void testHttpClientRequestIgnorePolicy2(VertxTestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.IGNORE, false, 0);
  }

  @Test
  public void testHttpClientRequestPropagatePolicy1(VertxTestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.PROPAGATE, true, 3);
  }

  @Test
  public void testHttpClientRequestPropagatePolicy2(VertxTestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.PROPAGATE, false, 0);
  }

  @Test
  public void testHttpClientRequestAlwaysPolicy1(VertxTestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.ALWAYS, true, 3);
  }

  @Test
  public void testHttpClientRequestAlwaysPolicy2(VertxTestContext ctx) throws Exception {
    testHttpClientRequest(ctx, TracingPolicy.ALWAYS, false, 2);
  }

  private void testHttpClientRequest(VertxTestContext ctx, TracingPolicy policy, boolean createTrace, int expectedTrace) throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    HttpClient c = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(policy));
    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE)).requestHandler(req -> {
      c.request(HttpMethod.GET, 8081, "localhost", "/", ctx.succeeding(clientReq -> {
        clientReq.send(ctx.succeeding(clientResp -> {
          req.response().end();
        }));
      }));
    })
      .listen(8080, ctx.succeeding(v -> latch.countDown()));
    vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(TracingPolicy.PROPAGATE)).requestHandler(req -> {
      req.response().end();
    }).listen(8081, ctx.succeeding(v -> latch.countDown()));

    latch.await();

    sendRequest(createTrace);

    otelTesting.assertTraces()
      .size()
      .isGreaterThanOrEqualTo(expectedTrace);
    if (expectedTrace > 0) {
      assertThat(otelTesting.getSpans())
        .anySatisfy(spanData -> {
          assertThat(spanData.getAttributes().get(SemanticAttributes.HTTP_METHOD))
            .isEqualTo("GET");
          assertThat(spanData.getAttributes().get(SemanticAttributes.HTTP_STATUS_CODE))
            .isEqualTo(200);
          assertThat(spanData.getAttributes().get(SemanticAttributes.HTTP_URL))
            .isEqualTo(createTrace ? "http://localhost:8080/" : "http://localhost:8081/");
        });
    }
    ctx.completeNow();
  }

  private void sendRequest(boolean withTrace) {
    Span span = null;
    try {
      TextMapPropagator.Setter<HttpURLConnection> setter = HttpURLConnection::setRequestProperty;

      URL url = new URL("http://localhost:8080");
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestMethod("GET");
      if (withTrace) {
        span = tracer.spanBuilder("test")
          .setSpanKind(Span.Kind.CLIENT)
          .setAttribute("component", "vertx")
          .startSpan();
        textMapPropagator.inject(io.opentelemetry.context.Context.current(), con, setter);
      }
      assertThat(con.getResponseCode()).isEqualTo(200);
      if (span != null) {
        span.end();
      }
    } catch (Exception e) {
      if (span != null) {
        span.end();
      }
    }
  }

  @Test
  public void testEventBus(VertxTestContext ctx) throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    vertx.createHttpServer().requestHandler(req ->
      vertx.eventBus().request("the-address", "ping", ctx.succeeding(resp ->
        req.response().end()
      ))
    ).listen(8080, ctx.succeeding(v -> latch.countDown()));
    vertx.eventBus().consumer("the-address", msg -> msg.reply("pong"));
    vertx.createHttpServer().requestHandler(req -> req.response().end())
      .listen(8081, ctx.succeeding(v -> latch.countDown()));

    latch.await();

    sendRequest(true);

    List<SpanData> spans = otelTesting.getSpans();
    SpanData spanData = spans.get(0);
    ctx.verify(() -> {
      assertThat(spanData.getAttributes().get(AttributeKey.stringKey("message_bus.destination")))
        .isEqualTo("the-address");
    });
    ctx.completeNow();
  }
}
