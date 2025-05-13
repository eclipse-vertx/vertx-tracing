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
package io.vertx.tests.opentracing;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import io.vertx.tracing.opentracing.OpenTracingTracerFactory;
import org.junit.*;
import org.junit.runner.RunWith;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(VertxUnitRunner.class)
public class SqlClientTest {

  private static PostgreSQLContainer<?> server;
  private static PgConnectOptions connectOptions;
  private Vertx vertx;
  private MockTracer tracer;
  private Pool pool;

  @BeforeClass
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

  @AfterClass
  public static void stopDB() {
    server.stop();
  }

  @Before
  public void before() {
    tracer = new MockTracer();
    vertx = Vertx.builder().withTracer(new OpenTracingTracerFactory(tracer)).build();
    pool = PgBuilder.pool().connectingTo(connectOptions).using(vertx).build();
  }

  @After
  public void after(TestContext ctx) {
    vertx.close().onComplete(ctx.asyncAssertSuccess());
  }

  List<MockSpan> waitUntil(int expected) throws Exception {
    long now = System.currentTimeMillis();
    while (tracer.finishedSpans().size() < expected && (System.currentTimeMillis() - now) < 10000 ) {
      Thread.sleep(10);
    }
    assertEquals(expected, tracer.finishedSpans().size());
    return tracer.finishedSpans();
  }

  void assertSingleSpan(List<MockSpan> spans) {
    long result = spans.stream().map(span -> span.context().traceId()).distinct().count();
    assertEquals(1, result);
  }

  @Test
  public void testPreparedQuery(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async();
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
    }).listen(8080).onComplete(ctx.asyncAssertSuccess(v -> listenLatch.complete()));
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(TracingPolicy.ALWAYS));
    client.request(HttpMethod.GET, 8080, "localhost", "/").onComplete(ctx.asyncAssertSuccess(req -> {
      req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(200, resp.statusCode());
        responseLatch.complete();
      }));
    }));
    responseLatch.awaitSuccess();
    List<MockSpan> spans = waitUntil(3);
    MockSpan requestSpan = spans.get(1);
    assertEquals("GET", requestSpan.operationName());
    assertEquals("GET", requestSpan.tags().get("http.method"));
    assertEquals("http://localhost:8080/", requestSpan.tags().get("http.url"));
    assertEquals("200", requestSpan.tags().get("http.status_code"));
    assertTrue(MILLISECONDS.convert(requestSpan.finishMicros() - requestSpan.startMicros(), MICROSECONDS) > baseDurationInMs);
    MockSpan querySpan = spans.get(0);
    assertEquals("Query", querySpan.operationName());
    assertEquals("client", querySpan.tags().get("span.kind"));
    assertEquals("SELECT $1 \"VAL\"", querySpan.tags().get("db.statement"));
    assertEquals("sql", querySpan.tags().get("db.type"));
    assertEquals("postgres", querySpan.tags().get("db.user"));
    assertEquals("postgres", querySpan.tags().get("db.instance"));
    assertEquals("postgresql", querySpan.tags().get("db.system"));
    assertEquals(querySpan.parentId(), requestSpan.context().spanId());
    assertEquals(querySpan.context().traceId(), requestSpan.context().traceId());
  }
}
