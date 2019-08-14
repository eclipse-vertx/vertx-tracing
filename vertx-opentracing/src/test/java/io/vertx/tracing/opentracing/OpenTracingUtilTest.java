package io.vertx.tracing.opentracing;

import static io.vertx.tracing.opentracing.OpenTracingUtil.ACTIVE_SPAN;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import io.opentracing.Span;
import io.opentracing.mock.MockTracer;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class OpenTracingUtilTest {

  private Vertx vertx;
  private MockTracer tracer;

  @Before
  public void before() {
    tracer = new MockTracer();
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new OpenTracingOptions(tracer).setEnabled(true)));
  }

  @After
  public void after(TestContext ctx) {
    vertx.close(ctx.asyncAssertSuccess());
  }

  @Test
  public void getSpan_should_retrieve_a_span_from_the_currentContext(TestContext ctx) {
    Span span = tracer.buildSpan("test").start();
    vertx.runOnContext(ignored -> {
      assertNull(OpenTracingUtil.getSpan());
      Context context = Vertx.currentContext();
      context.putLocal(ACTIVE_SPAN, span);

      assertSame(span, OpenTracingUtil.getSpan());
    });
  }

  @Test
  public void getSpan_should_return_null_when_there_is_no_current_context(TestContext ctx) {
    Span span = tracer.buildSpan("test").start();
    OpenTracingUtil.setSpan(span);
    assertNull(OpenTracingUtil.getSpan());
  }

  @Test
  public void setSpan_should_put_the_span_on_the_current_context() {
    Span span = tracer.buildSpan("test").start();
    vertx.runOnContext(ignored -> {
      assertNull(OpenTracingUtil.getSpan());
      OpenTracingUtil.setSpan(span);

      Context context = Vertx.currentContext();
      assertSame(span, context.getLocal(ACTIVE_SPAN));
    });
  }

  @Test
  public void clearContext_should_remove_any_span_from_the_context() {
    Span span = tracer.buildSpan("test").start();
    vertx.runOnContext(ignored -> {
      assertNull(OpenTracingUtil.getSpan());
      OpenTracingUtil.setSpan(span);

      OpenTracingUtil.clearContext();
      assertNull(OpenTracingUtil.getSpan());
    });
  }
}
