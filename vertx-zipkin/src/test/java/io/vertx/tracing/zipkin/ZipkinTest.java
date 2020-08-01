package io.vertx.tracing.zipkin;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.junit.ZipkinRule;

import java.util.ArrayList;
import java.util.LinkedList;
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
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(
      new ZipkinTracingOptions()
        .setServiceName("my-service-name")
        .setSupportsJoin(false)
        .setSenderOptions(new HttpSenderOptions().setSenderEndpoint(url))
    ));
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

  List<Span> assertSingleSpan(List<Span> spans) {
    long result = spans.stream().map(Span::traceId).distinct().count();
    assertEquals(1, result);

    // Find top
    spans = new ArrayList<>(spans);
    Span top = spans.stream().filter(span -> span.id().equals(span.traceId())).findFirst().get();
    spans.remove(top);
    LinkedList<Span> sorted = foo(top, spans);
    sorted.addFirst(top);
    return sorted;
  }

  private LinkedList<Span> foo(Span top, List<Span> others) {
    if (others.isEmpty()) {
      return new LinkedList<>();
    }
    Span s = others.stream().filter(span -> span.parentId().equals(top.id())).findFirst().get();
    others.remove(s);
    LinkedList<Span> ret = foo(s, others);
    ret.addFirst(s);
    return ret;
  }

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
    try {
      client.request(HttpMethod.GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(req ->{
        req.send(ctx.asyncAssertSuccess(resp -> {
          responseLatch.complete();
        }));
      }));
      responseLatch.awaitSuccess();
      List<Span> trace = waitUntilTrace(zipkin, 2);
      assertEquals(2, trace.size());
      Span span1 = trace.get(0);
      Span span2 = trace.get(1);
//    assertEquals("get", span.name());
//    assertEquals("GET", span.tags().get("http.method"));
//    assertEquals("/", span.tags().get("http.path"));
      responseLatch.await(10000);
    } finally {
      client.close();
    }
  }

  @Test
  public void testHttpClientRequest(TestContext ctx) throws Exception {
    Async listenLatch = ctx.async(2);
    HttpClient c = vertx.createHttpClient();
    HttpServer server = vertx.createHttpServer().requestHandler(req -> {
      c.request(HttpMethod.GET, 8081, "localhost", "/", ctx.asyncAssertSuccess(clientReq -> {
        clientReq.send(ctx.asyncAssertSuccess(clientResp -> {
          req.response().end();
        }));
      }));
    }).listen(8080, ar -> {
      ctx.assertTrue(ar.succeeded(), "Could not bind on port 8080");
      listenLatch.countDown();
    });
    vertx.createHttpServer().requestHandler(req -> {
      req.response().end();
    }).listen(8081, ar -> {
      ctx.assertTrue(ar.succeeded(), "Could not bind on port 8081");
      listenLatch.countDown();
    });
    listenLatch.awaitSuccess();
    Async responseLatch = ctx.async();
    client.request(HttpMethod.GET, 8080, "localhost", "/", ctx.asyncAssertSuccess(clientReq ->{
      clientReq.send(ctx.asyncAssertSuccess(clientResp -> {
        responseLatch.complete();
      }));
    }));
    responseLatch.awaitSuccess();
    List<Span> trace = assertSingleSpan(waitUntilTrace(4));
    assertEquals(4, trace.size());
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
    assertEquals("get", span3.name());
    assertEquals("GET", span3.tags().get("http.method"));
    assertEquals("/", span3.tags().get("http.path"));
    assertEquals(8081, span3.remoteEndpoint().portAsInt());
    Span span4 = trace.get(3);
    assertEquals(Span.Kind.SERVER, span4.kind());
    assertEquals("get", span4.name());
    assertEquals("GET", span4.tags().get("http.method"));
    assertEquals("/", span4.tags().get("http.path"));
  }
}
