package io.vertx.tracing.opentracing;

import io.jaegertracing.Configuration;
import io.jaegertracing.internal.JaegerTracer;
import io.opentracing.*;
import io.opentracing.log.Fields;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.vertx.core.Context;
import io.vertx.core.spi.tracing.TagExtractor;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * - https://github.com/opentracing/specification/blob/master/semantic_conventions.md
 * - https://github.com/opentracing/specification/blob/master/specification.md
 */
public class OpenTracingTracer implements io.vertx.core.spi.tracing.VertxTracer<Scope, Scope> {

  private static final String ACTIVE_SCOPE = "vertx.tracing.opentracing.scope";

  /**
   * Instantiate an OpenTracing tracer configured from ENV, e.g {@code JAEGER_SERVICE_NAME}, etc...
   */
  static Tracer createDefaultTracer() {
    Configuration config = Configuration.fromEnv();
    return config.getTracerBuilder().build();
  }

  private final boolean closeTracer;
  private final Tracer tracer;

  /**
   * Instantiate a OpenTracing tracer using the specified {@code tracer}.
   *
   * @param closeTracer close the tracer when necessary
   * @param tracer the tracer instance
   */
  public OpenTracingTracer(boolean closeTracer, Tracer tracer) {
    this.closeTracer = closeTracer;
    this.tracer = tracer;
  }

  @Override
  public <R> Scope receiveRequest(Context context, R request, String operation, Iterable<Map.Entry<String, String>> headers, TagExtractor<R> tagExtractor) {
    SpanContext sc = tracer.extract(Format.Builtin.HTTP_HEADERS, new TextMap() {
      @Override
      public Iterator<Map.Entry<String, String>> iterator() {
        return headers.iterator();
      }
      @Override
      public void put(String key, String value) {
        throw new UnsupportedOperationException();
      }
    });
    Tracer.SpanBuilder builder = tracer.buildSpan(operation);
    if (sc != null) {
      builder = builder.asChildOf(sc);
    }
    builder
      .ignoreActiveSpan()
      .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER)
      .withTag(Tags.COMPONENT.getKey(), "vertx");
    Scope scope = builder.startActive(false);
    reportTags(scope.span(), request, tagExtractor);
    scope.close();
    context.putLocal(ACTIVE_SCOPE, scope);
    return scope;
  }

  @Override
  public <R> void sendResponse(Context context, R response, Scope scope, Throwable failure, TagExtractor<R> tagExtractor) {
    if (scope != null) {
      context.removeLocal(ACTIVE_SCOPE);
      Span span = scope.span();
      if (failure != null) {
        reportFailure(span, failure);
      }
      reportTags(span, response, tagExtractor);
      span.finish();
    }
  }

  @Override
  public <R> Scope sendRequest(Context context, R request, String operation, BiConsumer<String, String> headers, TagExtractor<R> tagExtractor) {
    Scope scope = context.getLocal(ACTIVE_SCOPE);
    if (scope != null) {
      Tracer.SpanBuilder builder = tracer.buildSpan(operation)
        .asChildOf(scope.span())
        .withTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_CLIENT)
        .withTag(Tags.COMPONENT.getKey(), "vertx");
      Scope child = builder
        .startActive(false);
      reportTags(child.span(), request, tagExtractor);
      if (headers != null) {
        tracer.inject(child.span().context(), Format.Builtin.HTTP_HEADERS, new TextMap() {
          @Override
          public Iterator<Map.Entry<String, String>> iterator() {
            throw new UnsupportedOperationException();
          }
          @Override
          public void put(String key, String value) {
            headers.accept(key, value);
          }
        });
      }
      return child;
    }
    return null;
  }

  @Override
  public <R> void receiveResponse(Context context, R response, Scope scope, Throwable failure, TagExtractor<R> tagExtractor) {
    if (scope != null) {
      Span span = scope.span();
      if (failure != null) {
        reportFailure(span, failure);
      }
      reportTags(span, response, tagExtractor);
      span.finish();
    }
  }

  private <T> void reportTags(Span span, T obj, TagExtractor<T> tagExtractor) {
    int len = tagExtractor.len(obj);
    for (int idx = 0;idx < len;idx++) {
      span.setTag(tagExtractor.name(obj, idx), tagExtractor.value(obj, idx));
    }
  }

  private void reportFailure(Span span, Throwable failure) {
    if (failure != null) {
      span.setTag("error", true);
      HashMap<String, Object> fields = new HashMap<>();
      fields.put(Fields.EVENT, "error");
      fields.put(Fields.MESSAGE, failure.getMessage());
      fields.put(Fields.ERROR_KIND, "Exception");
      fields.put(Fields.ERROR_OBJECT, failure);
      span.log(fields);
    }
  }

  @Override
  public void close() {
    if (closeTracer) {
      if (tracer instanceof Closeable) {
        try {
          ((Closeable) tracer).close();
        } catch (IOException ignore) {
        }
      }
    }
  }
}
