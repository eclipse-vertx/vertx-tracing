package io.vertx.tracing.zipkin;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;

import brave.Span;
import brave.Tracing;
import brave.propagation.B3SingleFormat;
import brave.propagation.TraceContext;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ZipkinTracerUtilTest extends ZipkinBaseTest {

  protected static final String ADDRESS = "zipkin.tracer.util.test";

  @Test
  public void test(TestContext ctx) throws Exception {
    Async latch = ctx.async();
    Span span = Tracing.newBuilder().build().tracer().newTrace();
    String expectedTraceId = B3SingleFormat.writeB3SingleFormat(span.context());
    AtomicReference<String> actualTraceId = new AtomicReference<>();
    Promise<Void> startSender = Promise.promise();
    vertx.deployVerticle(new AbstractVerticle() {
      public void start() throws Exception {
        vertx.eventBus().<Boolean>consumer(ADDRESS, message -> {
          actualTraceId.set(ZipkinTracerUtil.exportTraceId());
          latch.complete();
        });
        startSender.complete();
      }
    });
    
    startSender.future().onComplete(res -> {
      vertx.deployVerticle(new AbstractVerticle() {
        public void start() throws Exception {
          ZipkinTracerUtil.importTraceId(expectedTraceId);
          vertx.eventBus().send(ADDRESS, true);
        }
      });
    });
    
    latch.awaitSuccess();
    
    TraceContext actualContext = B3SingleFormat.parseB3SingleFormat(actualTraceId.get()).context();
    assertEquals(span.context().traceId(), actualContext.traceId());
    
  }
}
