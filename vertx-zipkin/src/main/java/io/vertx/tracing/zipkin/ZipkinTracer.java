package io.vertx.tracing.zipkin;

import brave.Span;
import brave.Tracing;
import brave.http.*;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.tracing.TagExtractor;
import zipkin2.Endpoint;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * - https://zipkin.io/pages/instrumenting.html
 * - https://zipkin.io/public/thrift/v1/zipkinCore.html
 */
public class ZipkinTracer implements io.vertx.core.spi.tracing.VertxTracer<Span, BiConsumer<Object, Throwable>> {

  // docker run --rm -ti -p 9411:9411 openzipkin/zipkin

  private static final String ACTIVE_SPAN = "vertx.tracing.zipkin.active_span";
  private static final String ACTIVE_CONTEXT = "vertx.tracing.zipkin.active_context";
  private static final String ACTIVE_REQUEST = "vertx.tracing.zipkin.active_request";

  static final HttpServerAdapter<HttpServerRequest, HttpServerRequest> HTTP_SERVER_ADAPTER =
    new HttpServerAdapter<HttpServerRequest, HttpServerRequest>() {
      @Override
      public String method(HttpServerRequest request) {
        return request.method().name();
      }

      @Override
      public String url(HttpServerRequest request) {
        return request.absoluteURI();
      }

      @Override
      public String requestHeader(HttpServerRequest request, String name) {
        return request.headers().get(name);
      }

      @Override
      public Integer statusCode(HttpServerRequest request) {
        return request.response().getStatusCode();
      }

      @Override
      public String methodFromResponse(HttpServerRequest request) {
        return request.method().name();
      }

      @Override
      public String route(HttpServerRequest request) {
        int sc = request.response().getStatusCode();
        if (sc == 404 || sc / 100 == 3) {
          return "";
        } else {
          return request.path();
        }
      }

      @Override
      public boolean parseClientIpAndPort(HttpServerRequest request, Span span) {
        if (parseClientIpFromXForwardedFor(request, span)) {
          return true;
        }
        SocketAddress addr = request.remoteAddress();
        return span.remoteIpAndPort(addr.host(), addr.port());
      }

      @Override
      public boolean parseClientAddress(HttpServerRequest req, Endpoint.Builder builder) {
        if (super.parseClientAddress(req, builder)) return true;
        SocketAddress addr = req.remoteAddress();
        if (builder.parseIp(addr.host())) {
          builder.port(addr.port());
          return true;
        }
        return false;
      }
    };

  static final HttpClientAdapter<HttpClientRequest, HttpClientResponse> HTTP_CLIENT_ADAPTER =
    new HttpClientAdapter<HttpClientRequest, HttpClientResponse>() {

      @Override
      public String method(HttpClientRequest request) {
        HttpMethod method = request.method();
        return method == HttpMethod.OTHER ? request.getRawMethod() : method.name();
      }

      @Override
      public String url(HttpClientRequest request) {
        return request.absoluteURI();
      }

      @Override
      public String requestHeader(HttpClientRequest request, String name) {
        return request.headers().get(name);
      }

      @Override
      public Integer statusCode(HttpClientResponse response) {
        return response.statusCode();
      }


    };

  private static final Propagation.Getter<HttpServerRequest, String> GETTER = new Propagation.Getter<HttpServerRequest, String>() {
    @Override
    public String get(HttpServerRequest carrier, String key) {
      return carrier.getHeader(key);
    }

    @Override
    public String toString() {
      return "HttpServerRequest::getHeader";
    }
  };

  /**
   * @return the current active {@link Span} otherwise {@code null}
   */
  public static Span activeSpan() {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      return (Span) ctx.getLocal(ACTIVE_SPAN);
    }
    return null;
  }

  /**
   * @return the current active {@link TraceContext} otherwise {@code null}
   */
  public static TraceContext activeContext() {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      return (TraceContext) ctx.getLocal(ACTIVE_CONTEXT);
    }
    return null;
  }

  private final TraceContext.Extractor<HttpServerRequest> httpServerExtractor;
  private final Tracing tracing;
  private final boolean closeTracer;
  private final HttpServerHandler<HttpServerRequest, HttpServerRequest> httpServerHandler;
  private final HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler;

  public ZipkinTracer(boolean closeTracer, Tracing tracing) {
    this(closeTracer, HttpTracing.newBuilder(tracing).build());
  }

  public ZipkinTracer(boolean closeTracer, HttpTracing httpTracing) {
    this.closeTracer = closeTracer;
    this.tracing = httpTracing.tracing();
    this.clientHandler = HttpClientHandler.create(httpTracing, HTTP_CLIENT_ADAPTER);
    this.httpServerHandler = HttpServerHandler.create(httpTracing, HTTP_SERVER_ADAPTER);
    this.httpServerExtractor = httpTracing.tracing().propagation().extractor(GETTER);
  }

  @Override
  public <R> Span receiveRequest(Context context, R request, String operation, Iterable<Map.Entry<String, String>> headers, TagExtractor<R> tagExtractor) {
    if (request instanceof HttpServerRequest) {
      HttpServerRequest httpReq = (HttpServerRequest) request;
      Span span = httpServerHandler.handleReceive(httpServerExtractor, httpReq);
      if (span != null) {
        context.putLocal(ACTIVE_SPAN, span);
        context.putLocal(ACTIVE_REQUEST, request);
        context.putLocal(ACTIVE_CONTEXT, span.context());
      }
      return span;
    }
    return null;
  }

  @Override
  public <R> void sendResponse(Context context, R response, Span span, Throwable failure, TagExtractor<R> tagExtractor) {
    if (span != null) {
      context.removeLocal(ACTIVE_SPAN);
      if (response instanceof HttpServerResponse) {
        HttpServerRequest httpReq = context.getLocal(ACTIVE_REQUEST);
        context.removeLocal(ACTIVE_REQUEST);
        httpServerHandler.handleSend(httpReq, failure, span);
      }
    }
  }

  @Override
  public <R> BiConsumer<Object, Throwable> sendRequest(Context context, R request, String operation, BiConsumer<String, String> headers, TagExtractor<R> tagExtractor) {
    TraceContext o = context.getLocal(ACTIVE_CONTEXT);
    if (request instanceof HttpClientRequest) {
      HttpClientRequest httpReq = (HttpClientRequest) request;
      Span span;
      if (o == null) {
        span = tracing.tracer().newTrace();
      } else {
        span = tracing.tracer().newChild(o);
      }
      clientHandler.handleSend(tracing.propagation().injector(new Propagation.Setter<HttpClientRequest, String>() {
        @Override
        public void put(HttpClientRequest carrier, String key, String value) {
          headers.accept(key, value);
        }
        @Override
        public String toString() {
          return "HttpClientRequest::putHeader";
        }
      }), httpReq, span);
      SocketAddress socketAddress = httpReq.connection().remoteAddress();
      if (socketAddress != null) {
        span.remoteIpAndPort(socketAddress.host(), socketAddress.port());
      }
      return (resp, err) -> {
        clientHandler.handleReceive((HttpClientResponse) resp, err, span);
      };
    }
    return null;
  }

  @Override
  public <R> void receiveResponse(Context context, R response, BiConsumer<Object, Throwable> payload, Throwable failure, TagExtractor<R> tagExtractor) {
    if (payload != null) {
      payload.accept(response, failure);
    }
  }

  @Override
  public void close() {
    if (closeTracer) {
      tracing.close();
    }
  }
}
