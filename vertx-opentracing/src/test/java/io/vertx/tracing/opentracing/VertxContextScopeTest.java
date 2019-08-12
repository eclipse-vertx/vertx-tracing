package io.vertx.tracing.opentracing;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.mock.MockTracer;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class VertxContextScopeTest {

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
  public void scopes_should_properly_stack() {

    vertx.runOnContext(
        v -> {
          Span first = tracer.buildSpan("test1").start();
          try (Scope scope1 = manager.activate(first)) {
            assertSame(first, manager.activeSpan());
            Span second = tracer.buildSpan("test2").start();
            try (Scope scope2 = manager.activate(second)) {
              assertSame(second, manager.activeSpan());
              Span third = tracer.buildSpan("test3").start();
              try (Scope scope3 = manager.activate(third)) {
                assertSame(third, manager.activeSpan());
              }
              assertSame(second, manager.activeSpan());
            }
            assertSame(first, manager.activeSpan());
          }

          assertNull("Scope manager should be empty.", manager.activeSpan());
        });
  }
}
