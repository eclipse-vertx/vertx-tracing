package io.vertx.tracing.opentracing;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class SqlClientTest {

  private static PostgreSQLContainer<?> server;
  private static PgConnectOptions connectOptions;
  private Vertx vertx;
  private MockTracer tracer;
  private PgPool pool;

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
      .setHost(server.getContainerIpAddress())
      .setPort(server.getMappedPort(5432));

  }

  @AfterClass
  public static void stopDB() {
    server.stop();
  }

  @Before
  public void before() {
    tracer = new MockTracer();
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new OpenTracingOptions(tracer)));
    pool = PgPool.pool(vertx, connectOptions, new PoolOptions());
  }

  @After
  public void after(TestContext ctx) {
    vertx.close(ctx.asyncAssertSuccess());
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
    HttpClient client = vertx.createHttpClient();
    client.get(8080, "localhost", "/", ctx.asyncAssertSuccess(resp -> {
      ctx.assertEquals(200, resp.statusCode());
      responseLatch.complete();
    }));
    responseLatch.awaitSuccess();
    List<MockSpan> spans = waitUntil(2);
    MockSpan requestSpan = spans.get(0);
    assertEquals("GET", requestSpan.operationName());
    assertEquals("GET", requestSpan.tags().get("http.method"));
    assertEquals("http://localhost:8080/", requestSpan.tags().get("http.url"));
    assertEquals("200", requestSpan.tags().get("http.status_code"));
    MockSpan querySpan = spans.get(1);
    assertEquals("Query", querySpan.operationName());
    assertEquals("client", querySpan.tags().get("span.kind"));
    assertEquals("SELECT $1 \"VAL\"", querySpan.tags().get("db.statement"));
    assertEquals("sql", querySpan.tags().get("db.type"));
    assertEquals("postgres", querySpan.tags().get("db.user"));
    assertEquals("postgres", querySpan.tags().get("db.instance"));
    assertEquals(querySpan.parentId(), requestSpan.context().spanId());
    assertEquals(querySpan.context().traceId(), requestSpan.context().traceId());
  }
}
