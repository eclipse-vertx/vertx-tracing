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

import brave.Span;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.B3SingleFormat;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.tracing.zipkin.impl.HttpUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <a href="https://zipkin.io/pages/instrumenting.html">Instrumenting a library</a>.
 * <p>
 * <a href="https://zipkin.io/public/thrift/v1/zipkinCore.html">Thrift module: zipkinCore</a>
 */
public class ZipkinTracer implements io.vertx.core.spi.tracing.VertxTracer<Span, BiConsumer<Object, Throwable>> {

  // docker run --rm -ti -p 9411:9411 openzipkin/zipkin

  public static final String ACTIVE_SPAN = "vertx.tracing.zipkin.active_span";
  public static final String ACTIVE_CONTEXT = "vertx.tracing.zipkin.active_context";
  public static final String ACTIVE_REQUEST = "vertx.tracing.zipkin.active_request";

  private static class HttpServerRequestAdapter extends brave.http.HttpServerRequest {

    final HttpServerRequest request;

    HttpServerRequestAdapter(HttpServerRequest request) {
      this.request = request;
    }

    @Override
    public String method() {
        return request.method().name();
      }

    @Override
    public String path() {
      return request.path();
    }

    @Override
    public String url() {
        return request.absoluteURI();
      }

    @Override
    public String header(String name) {
        return request.headers().get(name);
      }

    @Override
    public boolean parseClientIpAndPort(Span span) {
      if (parseClientIpFromXForwardedFor(span)) {
          return true;
        }
        SocketAddress addr = request.remoteAddress();
        if (addr != null && addr.hostAddress() != null) {
          return span.remoteIpAndPort(addr.hostAddress(), addr.port());
        }
        return false;
      }

    @Override
    public Object unwrap() {
      return request;
    }
  }

  private static class HttpServerResponseAdapter extends brave.http.HttpServerResponse {

    final HttpServerRequest request;
    final HttpServerResponse response;
    final Throwable error;

    HttpServerResponseAdapter(HttpServerRequest request, HttpServerResponse response, Throwable error) {
      this.request = request;
      this.response = response;
      this.error = error;
    }

    @Override
    public brave.http.HttpServerRequest request() {
      // Not ideal to wrap on demand but the method is invoked only in tests
      return new HttpServerRequestAdapter(request);
    }

    @Override
    public String method() {
      return request.method().name();
    }

    @Override
    public int statusCode() {
      return response.getStatusCode();
    }

    @Override
    public Throwable error() {
      return error;
    }

    @Override
    public Object unwrap() {
      return response;
    }
  }

  private static class HttpClientRequestAdapter extends brave.http.HttpClientRequest {

    final HttpRequest request;
    final BiConsumer<String, String> headers;

    HttpClientRequestAdapter(HttpRequest request, BiConsumer<String, String> headers) {
      this.request = request;
      this.headers = headers;
    }

    @Override
    public String method() {
      return request.method().name();
    }

    @Override
    public String url() {
      return request.absoluteURI();
    }

    @Override
    public String path() {
      return HttpUtils.parsePath(request.uri());
    }

    @Override
    public String header(String name) {
      return request.headers().get(name);
    }

    @Override
    public void header(String name, String value) {
      headers.accept(name, value);
    }

    @Override
    public Object unwrap() {
      return request;
    }
  }

  private static class HttpClientResponseAdapter extends brave.http.HttpClientResponse {

    final HttpClientRequest request;
    final HttpResponse response;
    final Throwable err;

    public HttpClientResponseAdapter(HttpClientRequest request, HttpResponse response, Throwable err) {
      this.request = request;
      this.response = response;
      this.err = err;
    }

    @Override
    public HttpClientRequest request() {
      return request;
    }

    @Override
    public int statusCode() {
      return response.statusCode();
    }

    @Override
    public Throwable error() {
      return err;
    }

    @Override
    public Object unwrap() {
      return response;
    }
  }

  private static final Propagation.Getter<Map<String, String>, String> MAP_GETTER = Map::get;

  public VertxSender sender() {
    return sender;
  }

  /**
   * @return the current active {@link Span} otherwise {@code null}
   */
  public static Span activeSpan() {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      return ctx.getLocal(ACTIVE_SPAN);
    }
    return null;
  }

  /**
   * @return the current active {@link TraceContext} otherwise {@code null}
   */
  public static TraceContext activeContext() {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      return ctx.getLocal(ACTIVE_CONTEXT);
    }
    return null;
  }

  private final Tracing tracing;
  private final boolean closeTracer;
  private final VertxSender sender;
  private final HttpServerHandler<brave.http.HttpServerRequest, brave.http.HttpServerResponse> httpServerHandler;
  private final HttpClientHandler<HttpClientRequest, brave.http.HttpClientResponse> clientHandler;
  private final TraceContext.Extractor<Map<String, String>> mapExtractor;

  public ZipkinTracer(boolean closeTracer, Tracing tracing, VertxSender sender) {
    this(closeTracer, HttpTracing.newBuilder(tracing).build(), sender);
  }

  public ZipkinTracer(boolean closeTracer, HttpTracing httpTracing, VertxSender sender) {
    this.closeTracer = closeTracer;
    this.tracing = httpTracing.tracing();
    this.clientHandler = HttpClientHandler.create(httpTracing);
    this.httpServerHandler = HttpServerHandler.create(httpTracing);
    this.mapExtractor = tracing.propagation().extractor(MAP_GETTER);
    this.sender = sender;
  }

  public Tracing getTracing() {
    return tracing;
  }

  @Override
  public <R> Span receiveRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, Iterable<Map.Entry<String, String>> headers, TagExtractor<R> tagExtractor) {
    if (policy == TracingPolicy.IGNORE) {
      return null;
    }
    Span span;
    if (request instanceof HttpServerRequest) {
      HttpServerRequest httpReq = (HttpServerRequest) request;
      String traceId = httpReq.getHeader("X-B3-TraceId");
      if (traceId == null && policy == TracingPolicy.PROPAGATE) {
        return null;
      }
      span = httpServerHandler.handleReceive(new HttpServerRequestAdapter(httpReq));
    } else {
      Map<String, String> headerMap = new HashMap<>();
      for (Map.Entry<String, String> header : headers) {
        headerMap.put(header.getKey(), header.getValue());
      }
      TraceContextOrSamplingFlags extracted = mapExtractor.extract(headerMap);
      if (extracted.context() != null) {
        span = tracing.tracer().joinSpan(extracted.context());
      } else if (policy == TracingPolicy.ALWAYS) {
        span = tracing.tracer().newTrace();
        span.start();
      } else {
        return null;
      }
      span.kind(kind == SpanKind.RPC ? Span.Kind.SERVER : Span.Kind.CONSUMER);
      span.name(operation);
      reportTags(request, tagExtractor, span);
    }
    ((ContextInternal)context).putLocal(ACTIVE_SPAN, span);
    ((ContextInternal)context).putLocal(ACTIVE_REQUEST, request);
    ((ContextInternal)context).putLocal(ACTIVE_CONTEXT, span.context());
    return span;
  }

  @Override
  public <R> void sendResponse(Context context, R response, Span span, Throwable failure, TagExtractor<R> tagExtractor) {
    if (span != null) {
      ((ContextInternal)context).removeLocal(ACTIVE_SPAN);
      if (response instanceof HttpServerResponse) {
        httpServerHandler.handleSend(new HttpServerResponseAdapter(((ContextInternal) context).getLocal(ACTIVE_REQUEST), (HttpServerResponse) response, failure), span);
      } else {
        span.finish();
      }
      ((ContextInternal)context).removeLocal(ACTIVE_REQUEST);
    }
  }

  @Override
  public <R> BiConsumer<Object, Throwable> sendRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, BiConsumer<String, String> headers, TagExtractor<R> tagExtractor) {
    if (policy == TracingPolicy.IGNORE) {
      return null;
    }
    TraceContext activeCtx = ((ContextInternal)context).getLocal(ACTIVE_CONTEXT);
    Span span;
    if (activeCtx == null) {
      if (policy != TracingPolicy.ALWAYS) {
        return null;
      }
      span = tracing.tracer().newTrace();
    } else {
      span = tracing.tracer().newChild(activeCtx);
      span.start();
    }
    if (request instanceof HttpRequest) {
      HttpRequest httpRequest = (HttpRequest) request;
      SocketAddress socketAddress = httpRequest.remoteAddress();
      if (socketAddress != null && socketAddress.hostAddress() != null) {
        span.remoteIpAndPort(socketAddress.hostAddress(), socketAddress.port());
      }
      HttpClientRequestAdapter clientRequestAdapter = new HttpClientRequestAdapter(httpRequest, headers);
      clientHandler.handleSend(clientRequestAdapter, span);
      return (resp, err) -> {
        clientHandler.handleReceive(new HttpClientResponseAdapter(clientRequestAdapter, (HttpResponse) resp, err), span);
      };
    } else {
      span.kind(kind == SpanKind.RPC ? Span.Kind.CLIENT : Span.Kind.PRODUCER);
      span.name(operation);
      reportTags(request, tagExtractor, span);
      TraceContext.Injector<BiConsumer<String, String>> injector = tracing.propagation().injector(BiConsumer::accept);
      injector.inject(span.context(), headers);
      return (resp, err) -> {
        if (err != null) {
          span.error(err);
        }
        span.finish();
      };
    }
  }

  private static <R> void reportTags(R request, TagExtractor<R> tagExtractor, Span span) {
    int len = tagExtractor.len(request);
    for (int i = 0;i < len;i++) {
      String name = tagExtractor.name(request, i);
      String value = tagExtractor.value(request, i);
      switch (name) {
        case "db.query.text":
          span.tag("sql.query", value);
          break;
        case "db.namespace":
        case "messaging.destination.name":
          span.remoteServiceName(value);
          break;
        case "network.peer.address":
          Matcher matcher = P.matcher(value);
          if (matcher.matches()) {
            String host = matcher.group(1);
            int port = Integer.parseInt(matcher.group(2));
            span.remoteIpAndPort(host, port);
          }
          break;
      }
    }
  }

  private static final Pattern P = Pattern.compile("^([^:]+):([0-9]+)$");

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
    if (sender != null) {
      sender.close();
    }
  }

  /**
   * Remove any active context.
   */
  public static void clearContext() {
    ContextInternal c = (ContextInternal) Vertx.currentContext();
    if (c != null) {
      c.removeLocal(ACTIVE_CONTEXT);
    }
  }

  /**
   * Remove any active span.
   */
  public static void clearSpan() {
    ContextInternal c = (ContextInternal) Vertx.currentContext();
    if (c != null) {
      c.removeLocal(ACTIVE_SPAN);
    }
  }

  /**
   * Import traceId.
   */
  public static void importTraceId(String traceId) {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      ctx.putLocal(ACTIVE_CONTEXT, B3SingleFormat.parseB3SingleFormat(traceId).context());
    }
  }

  /**
   * Export active traceId otherwise {@code null}.
   */
  public static String exportTraceId() {
    TraceContext ctx = activeContext();
    if (ctx != null) {
      return B3SingleFormat.writeB3SingleFormat(ctx);
    }
    return null;
  }

  /**
   * Set active {@link TraceContext}.
   */
  public static void setTraceContext(TraceContext context) {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      ctx.putLocal(ACTIVE_CONTEXT, context);
    }
  }

  /**
   * Set active {@link Span}.
   */
  public static void setSpan(Span span) {
    ContextInternal ctx = (ContextInternal) Vertx.currentContext();
    if (ctx != null) {
      ctx.putLocal(ACTIVE_SPAN, span);
    }
  }
}
