package io.vertx.tracing.opentracing;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.mock.MockTracer;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.tracing.opentracing.VertxContextScopeManager.CapturedContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class VertxContextScopeManagerTest {

  private Vertx vertx;
  private VertxContextScopeManager manager;
  private MockTracer tracer;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
    manager = new VertxContextScopeManager();
    tracer = new MockTracer(manager);
  }

  @After
  public void teardown() {
    tracer.close();
  }

  @Test
  public void without_a_context_the_active_scope_will_not_be_cached() {
    Span span = tracer.buildSpan("test").start();

    manager.activate(span);
    assertNull("Active scope should be null", manager.activeSpan());
  }

  @Test
  public void when_run_on_a_context_the_active_scope_should_be_set(TestContext ctx) {
    Span span = tracer.buildSpan("test").start();

    vertx.runOnContext(
        v -> {
          assertNull(manager.activeSpan());
          try (Scope scope = manager.activate(span)) {
            assertNotNull(span);
            Span active = manager.activeSpan();
            assertSame(span, active);
          } catch (Exception e) {
            ctx.fail(e);
          }
          assertNull(manager.activeSpan());
        });
  }

  @Test
  public void withContext_captures_the_passed_context_and_releases_it_after_close() {
    Context context = vertx.getOrCreateContext();
    CapturedContext captured = VertxContextScopeManager.withContext(context);
    try {
      assertSame(captured.get(), context);
    } finally {
      captured.close();
    }
    assertNull(captured.get());
  }

  @Test
  public void when_run_with_context_the_active_scope_should_be_set() {
    Span span = tracer.buildSpan("test").start();

    Context context = vertx.getOrCreateContext();
    try (CapturedContext ignored = VertxContextScopeManager.withContext(context)) {
      assertNotNull("The context should not be null.", context);
      assertNull("The active span should be null", manager.activeSpan());
      try (Scope scope = manager.activate(span)) {
        assertNotNull("Active scope should not be null", span);
        Span active = manager.activeSpan();
        assertSame(span, active);
      }
      assertNull(manager.activeSpan());
    }
  }
}
