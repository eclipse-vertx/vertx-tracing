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
package io.vertx.tests.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
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
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import io.vertx.tracing.opentelemetry.OpenTelemetryOptions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class SqlClientTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  private static PostgreSQLContainer<?> server;
  private static PgConnectOptions connectOptions;

  private Vertx vertx;
  private Pool pool;

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
      .setHost(server.getHost())
      .setPort(server.getMappedPort(5432));

  }

  @AfterAll
  public static void stopDB() {
    server.stop();
  }

  @BeforeEach
  public void before() {
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new OpenTelemetryOptions(otelTesting.getOpenTelemetry())));
    pool = PgBuilder.pool().connectingTo(connectOptions).using(vertx).build();
  }

  @AfterEach
  public void after(VertxTestContext context) throws Exception {
    vertx.close().onComplete(context.succeedingThenComplete());
  }

  @Test
  public void testPreparedQuery(VertxTestContext ctx) throws Exception {
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
    }).listen(8080).onComplete(ctx.succeeding(srv -> {
      HttpClient client = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(TracingPolicy.ALWAYS));
      client.request(HttpMethod.GET, 8080, "localhost", "/").compose(req -> {
        return req.send().compose(resp -> resp.body().map(resp));
      }).onComplete(ctx.succeeding(resp -> {
        ctx.verify(() -> {
          assertEquals(200, resp.statusCode());
          List<SpanData> spans = otelTesting.getSpans();
          assertEquals(3, spans.size());
          SpanData requestSpan = spans.get(1);
          assertEquals("GET", requestSpan.getName());
          assertEquals("GET", requestSpan.getAttributes().get(AttributeKey.stringKey("http.request.method")));
          assertEquals("http://localhost:8080/", requestSpan.getAttributes().get(AttributeKey.stringKey("http.url")));
          assertEquals("200", requestSpan.getAttributes().get(AttributeKey.stringKey("http.response.status_code")));
          assertTrue(MILLISECONDS.convert(requestSpan.getEndEpochNanos() - requestSpan.getStartEpochNanos(), NANOSECONDS) > baseDurationInMs);
          SpanData querySpan = spans.get(0);
          assertEquals("Query", querySpan.getName());
          assertEquals("client", querySpan.getAttributes().get(AttributeKey.stringKey("span.kind")));
          assertEquals("SELECT $1 \"VAL\"", querySpan.getAttributes().get(AttributeKey.stringKey("db.query.text")));
          assertEquals("postgres", querySpan.getAttributes().get(AttributeKey.stringKey("db.user")));
          assertEquals("postgres", querySpan.getAttributes().get(AttributeKey.stringKey("db.namespace")));
          assertEquals("postgresql", querySpan.getAttributes().get(AttributeKey.stringKey("db.system")));
          assertEquals(querySpan.getParentSpanId(), requestSpan.getSpanId());
          assertEquals(querySpan.getTraceId(), requestSpan.getTraceId());
          ctx.completeNow();
        });
      }));
    }));
  }
}
