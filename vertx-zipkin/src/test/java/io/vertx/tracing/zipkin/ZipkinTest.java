package io.vertx.tracing.zipkin;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Span;
import zipkin2.junit.ZipkinRule;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(VertxUnitRunner.class)
public class ZipkinTest {

  @Rule
  public ZipkinRule zipkin = new ZipkinRule();

  private Vertx vertx;
  private HttpClient client;

  @Before
  public void before() {
    String url = zipkin.httpUrl() + "/api/v2/spans";
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new ZipkinTracingOptions().setSenderOptions(new HttpSenderOptions().setSenderEndpoint(url)).setEnabled(true)));
    client = vertx.createHttpClient();
  }

  @After
  public void after(TestContext ctx) {
    client.close();
    vertx.close(ctx.asyncAssertSuccess());
  }

  List<Span> waitUntilTrace(int min) throws Exception {
    return waitUntilTrace(zipkin, min);
  }

  static List<Span> waitUntilTrace(ZipkinRule zipkin, int min) throws Exception {
    long now = System.currentTimeMillis();
    while ((System.currentTimeMillis() - now) < 10000 ) {
      List<List<Span>> traces = zipkin.getTraces();
      if (traces.size() > 0 && traces.get(0).size() >= min) {
        return traces.get(0);
      }
      Thread.sleep(10);
    }
    throw new AssertionError();
  }
/*
  void assertSingleSpan(List<MockSpan> spans) {
    long result = spans.stream().map(span -> span.context().traceId()).distinct().count();
    assertEquals(1, result);
  }
*/

  @Test
  public void testHttpServerRequest(TestContext ctx) throws Exception {
    testHttpServerRequest(zipkin, vertx, ctx);
  }

  public static void testHttpServerRequest(ZipkinRule zipkin, Vertx vertx, TestContext ctx) throws Exception {
    Async listenLatch = ctx.async();
    vertx.createHttpServer().requestHandler(req -> {
      req.response().end();
    }).listen(8080, ctx.asyncAssertSuccess(v -> listenLatch.complete()));
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    HttpClient client = vertx.createHttpClient();
    client.getNow(8080, "localhost", "/", ctx.asyncAssertSuccess(resp ->{
      responseLatch.complete();
    }));
    responseLatch.awaitSuccess();
    List<Span> trace = waitUntilTrace(zipkin, 2);
    assertEquals(2, trace.size());
    Span span1 = trace.get(0);
    Span span2 = trace.get(1);
//    assertEquals("get", span.name());
//    assertEquals("GET", span.tags().get("http.method"));
//    assertEquals("/", span.tags().get("http.path"));
  }

  @Test
  public void testHttpClientRequest(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async(2);
    HttpClient c = vertx.createHttpClient();
    vertx.createHttpServer().requestHandler(req -> {
      c.getNow(8081, "localhost", "/", ctx.asyncAssertSuccess(resp -> {
        req.response().end();
      }));
    }).listen(8080, ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    vertx.createHttpServer().requestHandler(req -> {
      req.response().end();
    }).listen(8081, ctx.asyncAssertSuccess(v -> listenLatch.countDown()));
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    client.getNow(8080, "localhost", "/", ctx.asyncAssertSuccess(resp ->{
      responseLatch.complete();
    }));
    responseLatch.awaitSuccess();
    List<Span> trace = waitUntilTrace(2);
    assertEquals(2, trace.size());
    Span span1 = trace.get(0);
//    assertEquals("get /", span1.name());
//    assertEquals("GET", span1.tags().get("http.method"));
//    assertEquals("/", span1.tags().get("http.path"));
    Span span2 = trace.get(1);
//    assertEquals("get", span2.name());
//    assertEquals("GET", span2.tags().get("http.method"));
//    assertEquals("/", span2.tags().get("http.path"));
  }
}
