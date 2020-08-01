package io.vertx.tracing.zipkin;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.codec.Encoding;
import zipkin2.reporter.Sender;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An HTTP sender using Vert.x HttpClient, only JSON encoding is supported.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxSender extends Sender {

  private static final CharSequence APPLICATION_JSON = HttpHeaders.createOptimized("application/json");

  private final int messageMaxBytes = 5242880;
  private final Vertx vertx;
  private final HttpClient client;
  private final String endpoint;

  public VertxSender(HttpSenderOptions options) {
    this.endpoint = options.getSenderEndpoint();
    this.vertx = Vertx.vertx(new VertxOptions().setTracingOptions(null));
    this.client = vertx.createHttpClient(options);
  }

  @Override
  public Encoding encoding() {
    return Encoding.JSON;
  }

  @Override
  public int messageMaxBytes() {
    return messageMaxBytes;
  }

  @Override
  public int messageSizeInBytes(List<byte[]> encodedSpans) {
    int val = 2;
    int length = encodedSpans.size();
    for(int i = 0;i < length;i++) {
      if (i > 0) {
        ++val;
      }
      val += encodedSpans.get(i).length;
    }
    return val;
  }

  @Override
  public Call<Void> sendSpans(List<byte[]> encodedSpans) {
    int capacity = messageSizeInBytes(encodedSpans);
    Buffer body = Buffer.buffer(capacity);
    body.appendByte((byte) '[');
    for (int i = 0;i < encodedSpans.size();i++) {
      if (i > 0) {
        body.appendByte((byte) ',');
      }
      body.appendBytes(encodedSpans.get(i));
    }
    body.appendByte((byte) ']');
    return new PostCall(body);
   }

  private class PostCall extends Call<Void> implements Handler<AsyncResult<Callback<Void>>> {

    private final Promise<Callback<Void>> promise = Promise.promise();
    private final Future<Callback<Void>> fut = promise.future().onComplete(this);
    private final Buffer body;

    PostCall(Buffer body) {
      this.body = body;
    }

    @Override
    public void handle(AsyncResult<Callback<Void>> ar) {
      if (ar.succeeded()) {
        Callback<Void> callback = ar.result();
        RequestOptions options = new RequestOptions()
          .setMethod(HttpMethod.POST)
          .addHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
          options.setAbsoluteURI(endpoint);
        } else {
          options.setURI(endpoint);
        }
        client.request(options)
          .compose(req -> req
            .send(body)
            .compose(HttpClientResponse::body))
          .onComplete(res -> {
          if (res.succeeded()) {
            callback.onSuccess(null);
          } else {
            callback.onError(res.cause());
          }
        });
      }
    }

    @Override
    public Void execute() throws IOException {
      CompletableFuture<Void> fut = new CompletableFuture<>();
      enqueue(new Callback<Void>() {
        @Override
        public void onSuccess(Void value) {
          fut.complete(null);
        }
        @Override
        public void onError(Throwable t) {
          fut.completeExceptionally(t);
        }
      });
      try {
        return fut.get(20, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new InterruptedIOException();
      } catch (ExecutionException e) {
        throw new IOException(e.getCause());
      } catch (TimeoutException e) {
        throw new IOException(e);
      }
    }

    @Override
    public void enqueue(Callback<Void> callback) {
      if (!promise.tryComplete(callback)) {
        throw new IllegalStateException();
      }
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isCanceled() {
      return false;
    }

    @Override
    public Call<Void> clone() {
      return new PostCall(body);
    }
  }

  @Override
  public void close() throws IOException {
    client.close();
    vertx.close();
  }
}
