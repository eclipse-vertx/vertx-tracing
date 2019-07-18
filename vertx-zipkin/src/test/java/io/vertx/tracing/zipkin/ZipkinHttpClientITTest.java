package io.vertx.tracing.zipkin;

import brave.propagation.CurrentTraceContext;
import brave.propagation.TraceContext;
import brave.test.http.ITHttpAsyncClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ZipkinHttpClientITTest extends ITHttpAsyncClient<HttpClient> {

  private Vertx vertx;

  @Rule
  public TestName testName = new TestName();

  public ZipkinHttpClientITTest() {
  }

  @Override
  protected HttpClient newClient(int port) {
    if (vertx == null) {
      vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new ZipkinTracingOptions(httpTracing).setEnabled(true)));
    }
    return vertx.createHttpClient(new HttpClientOptions()
      .setDefaultPort(port)
      .setDefaultHost("127.0.0.1")
    );
  }

  @Override
  protected void closeClient(HttpClient client) throws Exception {
    if (client != null) {
      client.close();
    }
  }

  @Override
  protected void getAsync(HttpClient client, String pathIncludingQuery) throws Exception {
    request(client, pathIncludingQuery, null);
  }

  private CompletableFuture<HttpClientResponse> request(HttpClient client, String pathIncludingQuery, String body) {
    CompletableFuture<HttpClientResponse> fut = new CompletableFuture<>();
    Runnable task = () -> {
      Handler<AsyncResult<HttpClientResponse>> handler = res -> {
        if (res.succeeded()) {
          fut.complete(res.result());
        } else {
          fut.completeExceptionally(res.cause());
        }
      };
      if (body == null) {
        client.get(pathIncludingQuery, handler).setFollowRedirects(true).end();
      } else {
        client.post(pathIncludingQuery, handler).end(body);
      }
    };
    TraceContext ctx = currentTraceContext.get();
    if (ctx != null) {
      vertx.runOnContext(v -> {
        CurrentTraceContext.Scope scope = currentTraceContext.newScope(ctx);
        task.run();
        fut.whenComplete((a, b) -> {
          scope.close();
        });

      });
    } else {
      task.run();
    }
    return fut;
  }

  @Override
  protected void get(HttpClient client, String pathIncludingQuery) throws Exception {
    request(client, pathIncludingQuery, null).get(10, TimeUnit.SECONDS);
    if (testName.getMethodName().equals("redirect")) {
      // Required to avoid race condition since the span created by the redirect test might be closed
      // before the client reports the 2 spans
      Thread.sleep(1000);
    }
  }

  @Override
  protected void post(HttpClient client, String pathIncludingQuery, String body) throws Exception {
    request(client, pathIncludingQuery, body).get(10, TimeUnit.SECONDS);
  }

  @Override
  public void close() throws Exception {
    if (vertx != null) {
      CountDownLatch latch = new CountDownLatch(1);
      vertx.close(ar -> {
        latch.countDown();
      });
      latch.await(10, TimeUnit.SECONDS);
      vertx = null;
    }
    super.close();
  }

  @Override
  protected String url(String path) {
    return "http://127.0.0.1:" + server.getPort() + path;
  }

  //  @After
//  public void stop() throws Exception {
//  }
}
