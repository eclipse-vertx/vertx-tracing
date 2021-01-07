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
package io.vertx.tracing.zipkin;

import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import zipkin2.Span;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SqlClientTest extends ZipkinBaseTest {

  private static PostgreSQLContainer<?> server;
  private static PgConnectOptions connectOptions;
  private PgPool pool;

  @BeforeClass
  public static void startDB() throws Exception {
    server = new PostgreSQLContainer<>("postgres:10")
      .withDatabaseName("postgres")
      .withUsername("postgres")
      .withPassword("postgres");
    server.start();
    InetAddress ip = Inet4Address.getByName(server.getContainerIpAddress());
    connectOptions = new PgConnectOptions()
      .setUser("postgres")
      .setPassword("postgres")
      .setDatabase("postgres")
      .setHost(ip.getHostAddress())
      .setPort(server.getMappedPort(5432));

  }

  @AfterClass
  public static void stopDB() {
    server.stop();
  }

  @Before
  public void before() {
    super.before();
    pool = PgPool.pool(vertx, connectOptions, new PoolOptions());
  }

  @Test
  public void testPreparedQuery(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async();
    vertx.createHttpServer().requestHandler(req -> {
      pool.preparedQuery("SELECT $1 \"VAL\"")
        .execute(Tuple.of("Hello World"))
        .onComplete(ar -> {
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
    }).listen(8080, ctx.asyncAssertSuccess(v -> listenLatch.complete()));
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    HttpClient client = vertx.createHttpClient(new HttpClientOptions().setTracingPolicy(TracingPolicy.ALWAYS));
    client.request(HttpMethod.GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req -> {
      req.send(ctx.asyncAssertSuccess(resp -> {
        ctx.assertEquals(200, resp.statusCode());
        responseLatch.complete();
      }));
    }));
    responseLatch.awaitSuccess();
    List<Span> trace = assertSingleSpan(waitUntilTrace(3));
    assertEquals(3, trace.size());
    Span span1 = trace.get(0);
    assertEquals(Span.Kind.CLIENT, span1.kind());
    assertEquals("my-service-name", span1.localServiceName());
    assertEquals("get", span1.name());
    assertEquals("GET", span1.tags().get("http.method"));
    assertEquals("/", span1.tags().get("http.path"));
    assertEquals(8080, span1.remoteEndpoint().portAsInt());
    Span span2 = trace.get(1);
    assertEquals(Span.Kind.SERVER, span2.kind());
    assertEquals("get", span2.name());
    assertEquals("GET", span2.tags().get("http.method"));
    assertEquals("/", span2.tags().get("http.path"));
    Span span3 = trace.get(2);
    assertEquals(Span.Kind.CLIENT, span3.kind());
    assertEquals("postgres", span3.remoteServiceName());
    assertEquals(connectOptions.getHost(), span3.remoteEndpoint().ipv4());
    assertEquals(connectOptions.getPort(), span3.remoteEndpoint().portAsInt());
    assertEquals("SELECT $1 \"VAL\"", span3.tags().get("sql.query"));
    assertNotEquals(0L, span3.durationAsLong());
    assertNotEquals(0L, span3.timestampAsLong());
  }
}
