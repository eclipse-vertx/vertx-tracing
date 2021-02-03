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
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class SqlClientTest {

  private static PostgreSQLContainer<?> server;
  private static PgConnectOptions connectOptions;

  @RegisterExtension
  final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();
  private Tracer tracer;
  private Vertx vertx;
  private TextMapPropagator textMapPropagator;
  private PgPool pool;

  @BeforeAll
  public static void startDB() {
    server = new PostgreSQLContainer<>("postgres:10")
      .withDatabaseName("postgres")
      .withUsername("postgres")
      .withPassword("postgres");
    server.start();
    connectOptions = new PgConnectOptions()
      .setUser("postgres")
      .setPassword("postgres")
      .setDatabase("postgres")
      .setHost(server.getContainerIpAddress())
      .setPort(server.getMappedPort(5432));

  }

  @AfterAll
  public static void stopDB() {
    server.stop();
  }

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
  public void testPreparedQuery(VertxTestContext ctx) throws Exception {
    CountDownLatch startServerLatch = new CountDownLatch(1);
    long baseDurationInMs = 500;
    vertx.createHttpServer().requestHandler(req -> {
      pool.preparedQuery("SELECT $1 \"VAL\"")
        .execute(Tuple.of("Hello World"))
        .onComplete(ar -> {
          vertx.setTimer(baseDurationInMs, (__) -> {
            if (ar.succeeded()) {
              RowSet<Row> rows = ar.result();
              req.response()
                .end();
            } else {
              req.response()
                .setStatusCode(500)
                .end();
            }
          });
        });
    }).listen(8080, ctx.succeeding(v -> startServerLatch.countDown()));
    startServerLatch.await();

    CountDownLatch responseLatch = new CountDownLatch(1);
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(TracingPolicy.ALWAYS));
    client.request(HttpMethod.GET, 8080, "localhost", "/", ctx.succeeding(req -> {
      req.send(ctx.succeeding(resp -> {
        ctx.verify(() ->
          assertThat(resp.statusCode()).isEqualTo(200)
        );
        responseLatch.countDown();
      }));
    }));
    responseLatch.await();

    SpanData requestSpan = otelTesting.getSpans().get(1);
    assertThat(requestSpan.getAttributes().get(AttributeKey.stringKey("operation"))).isEqualTo("GET");
    assertThat(requestSpan.getAttributes().get(SemanticAttributes.HTTP_METHOD)).isEqualTo("GET");
    assertThat(requestSpan.getAttributes().get(SemanticAttributes.HTTP_URL)).isEqualTo("http://localhost:8080/");
    assertThat(requestSpan.getAttributes().get(SemanticAttributes.HTTP_STATUS_CODE)).isEqualTo(200);
    assertThat(NANOSECONDS.convert(requestSpan.getEndEpochNanos() - requestSpan.getStartEpochNanos(), MILLISECONDS))
      .isGreaterThan(baseDurationInMs);

    SpanData querySpan = otelTesting.getSpans().get(0);
    assertThat(querySpan.getAttributes().get(AttributeKey.stringKey("operation"))).isEqualTo("Query");
    assertThat(querySpan.getKind())
      .isEqualTo(Span.Kind.CLIENT);
    assertThat(querySpan.getAttributes().get(AttributeKey.stringKey("db.statement")))
      .isEqualTo("SELECT $1 \"VAL\"");
    assertThat(querySpan.getAttributes().get(AttributeKey.stringKey("db.type")))
      .isEqualTo("sql");
    assertThat(querySpan.getAttributes().get(AttributeKey.stringKey("db.user")))
      .isEqualTo("postgres");
    assertThat(querySpan.getAttributes().get(AttributeKey.stringKey("db.instance")))
      .isEqualTo("postgres");
    assertThat(querySpan.getParentSpanId())
      .isEqualTo(requestSpan.getSpanId());
    assertThat(querySpan.getTraceId())
      .isEqualTo(requestSpan.getTraceId());

    ctx.completeNow();
  }
}
