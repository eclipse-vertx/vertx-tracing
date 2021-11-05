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
import brave.http.*;
import brave.propagation.B3SingleFormat;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.observability.HttpRequest;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.tracing.TracingPolicy;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * - https://zipkin.io/pages/instrumenting.html
 * - https://zipkin.io/public/thrift/v1/zipkinCore.html
 */
public class ZipkinTracer implements io.vertx.core.spi.tracing.VertxTracer<Span, BiConsumer<Object, Throwable>> {

  // docker run --rm -ti -p 9411:9411 openzipkin/zipkin

  public static final String ACTIVE_SPAN = "vertx.tracing.zipkin.active_span";
  public static final String ACTIVE_CONTEXT = "vertx.tracing.zipkin.active_context";
  public static final String ACTIVE_REQUEST = "vertx.tracing.zipkin.active_request";

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
        // Not implemented
        return "";
      }

      @Override
      public boolean parseClientIpAndPort(HttpServerRequest request, Span span) {
        if (parseClientIpFromXForwardedFor(request, span)) {
          return true;
        }
        SocketAddress addr = request.remoteAddress();
        if (addr != null && addr.hostAddress() != null) {
          return span.remoteIpAndPort(addr.hostAddress(), addr.port());
        }
        return false;
      }
    };

  static final HttpClientAdapter<HttpRequest, HttpResponse> HTTP_CLIENT_ADAPTER =
    new HttpClientAdapter<HttpRequest, HttpResponse>() {

      @Override
      public String method(HttpRequest request) {
        HttpMethod method = request.method();
        return method.name();
      }

      @Override
      public String url(HttpRequest request) {
        return request.absoluteURI();
      }

      @Override
      public String requestHeader(HttpRequest request, String name) {
        return request.headers().get(name);
      }

      @Override
      public Integer statusCode(HttpResponse response) {
        return response.statusCode();
      }
    };

  private static final Propagation.Getter<HttpServerRequest, String> HTTP_SERVER_GETTER = new Propagation.Getter<HttpServerRequest, String>() {
    @Override
    public String get(HttpServerRequest carrier, String key) {
      return carrier.getHeader(key);
    }
  };

  private static final Propagation.Getter<Map<String, String>, String> MAP_GETTER = new Propagation.Getter<Map<String, String>, String>() {
    @Override
    public String get(Map<String, String> carrier, String key) {
      return carrier.get(key);
    }
  };

  public VertxSender sender() {
    return sender;
  }

  /**
   * @return the current active {@link Span} otherwise {@code null}
   */
  public static Span activeSpan() {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      return ctx.getLocal(ACTIVE_SPAN);
    }
    return null;
  }

  /**
   * @return the current active {@link TraceContext} otherwise {@code null}
   */
  public static TraceContext activeContext() {
    Context ctx = Vertx.currentContext();
    if (ctx != null) {
      return ctx.getLocal(ACTIVE_CONTEXT);
    }
    return null;
  }
  
  public static void importTraceId(String traceId) {
	  Context ctx = Vertx.currentContext();
	  if (ctx != null) {
		  ctx.putLocal(ACTIVE_CONTEXT, B3SingleFormat.parseB3SingleFormat(traceId).context());
	  }
  }
  
  public static String exportTraceId() {
	  TraceContext ctx = activeContext();
	  if (ctx != null) {
		  return B3SingleFormat.writeB3SingleFormat(ctx);
	  }
	  return null;
  }

  private final TraceContext.Extractor<HttpServerRequest> httpServerExtractor;
  private final Tracing tracing;
  private final boolean closeTracer;
  private final VertxSender sender;
  private final HttpServerHandler<HttpServerRequest, HttpServerRequest> httpServerHandler;
  private final HttpClientHandler<HttpRequest, HttpResponse> clientHandler;
  private final TraceContext.Extractor<Map<String, String>> mapExtractor;

  public ZipkinTracer(boolean closeTracer, Tracing tracing, VertxSender sender) {
    this(closeTracer, HttpTracing.newBuilder(tracing).build(), sender);
  }

  public ZipkinTracer(boolean closeTracer, HttpTracing httpTracing, VertxSender sender) {
    this.closeTracer = closeTracer;
    this.tracing = httpTracing.tracing();
    this.clientHandler = HttpClientHandler.create(httpTracing, HTTP_CLIENT_ADAPTER);
    this.httpServerHandler = HttpServerHandler.create(httpTracing, HTTP_SERVER_ADAPTER);
    this.httpServerExtractor = httpTracing.tracing().propagation().extractor(HTTP_SERVER_GETTER);
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
      span = httpServerHandler.handleReceive(httpServerExtractor, httpReq);
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
    context.putLocal(ACTIVE_SPAN, span);
    context.putLocal(ACTIVE_REQUEST, request);
    context.putLocal(ACTIVE_CONTEXT, span.context());
    return span;
  }

  @Override
  public <R> void sendResponse(Context context, R response, Span span, Throwable failure, TagExtractor<R> tagExtractor) {
    if (span != null) {
      context.removeLocal(ACTIVE_SPAN);
      if (response instanceof HttpServerResponse) {
        HttpServerRequest httpReq = context.getLocal(ACTIVE_REQUEST);
        httpServerHandler.handleSend(httpReq, failure, span);
      } else {
        span.finish();
      }
      context.removeLocal(ACTIVE_REQUEST);
    }
  }

  @Override
  public <R> BiConsumer<Object, Throwable> sendRequest(Context context, SpanKind kind, TracingPolicy policy, R request, String operation, BiConsumer<String, String> headers, TagExtractor<R> tagExtractor) {
    if (policy == TracingPolicy.IGNORE) {
      return null;
    }
    TraceContext activeCtx = context.getLocal(ACTIVE_CONTEXT);
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
      Propagation.Setter<HttpRequest, String> setter = new Propagation.Setter<HttpRequest, String>() {
        @Override
        public void put(HttpRequest carrier, String key, String value) {
          headers.accept(key, value);
        }
        @Override
        public String toString() {
          return "HttpClientRequest::putHeader";
        }
      };
      TraceContext.Injector<HttpRequest> injector = tracing.propagation().injector(setter);
      clientHandler.handleSend(injector, httpRequest, span);
      return (resp, err) -> {
        clientHandler.handleReceive((HttpResponse) resp, err, span);
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
        case "db.statement":
          span.tag("sql.query", value);
          break;
        case "db.instance":
          span.remoteServiceName(value);
          break;
        case "peer.address":
          Matcher matcher = P.matcher(value);
          if (matcher.matches()) {
            String host = matcher.group(1);
            int port = Integer.parseInt(matcher.group(2));
            span.remoteIpAndPort(host, port);
          }
          break;
        case "message_bus.destination":
          span.remoteServiceName(value);
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
      try {
        sender.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
