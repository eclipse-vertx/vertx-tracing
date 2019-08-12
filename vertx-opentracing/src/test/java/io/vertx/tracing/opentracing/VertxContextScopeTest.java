package io.vertx.tracing.opentracing;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Test;

public class VertxContextScopeTest {

  private Vertx vertx;
  private VertxContextScopeManager manager;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
    manager = new VertxContextScopeManager();
  }

  @Test
  public void scopes_should_properly_stack() {
    Span first = mock(Span.class);
    Span second = mock(Span.class);
    Span third = mock(Span.class);

    vertx.runOnContext(
        v -> {
          try (Scope scope1 = manager.activate(first)) {
            assertSame(first, manager.activeSpan());
            try (Scope scope2 = manager.activate(second)) {
              assertSame(second, manager.activeSpan());
              try (Scope scope3 = manager.activate(third)) {
                assertSame(third, manager.activeSpan());
              }
              assertSame(second, manager.activeSpan());
            }
            assertSame(first, manager.activeSpan());
          }

          assertNull("Scope manager should be empty.", manager.activeSpan());
          verify(first, never()).finish();
          verify(second, never()).finish();
          verify(third, never()).finish();
        });
  }
}
