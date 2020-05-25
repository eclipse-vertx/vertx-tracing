package io.vertx.tracing.zipkin;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.junit.ZipkinRule;

@RunWith(VertxUnitRunner.class)
public class VertxSenderTest {

  private Vertx vertx;

  @Before
  public void before() {
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new ZipkinTracingOptions()));
  }

  @After
  public void after(TestContext ctx) {
    vertx.close(ctx.asyncAssertSuccess());
  }

  @Test
  public void testDefaultSenderEndpoint(TestContext ctx) throws Exception {
    ZipkinRule zipkin = new ZipkinRule();
    zipkin.start(9411);
    try {
      ZipkinTest.testHttpServerRequest(zipkin, vertx, ctx);
    } finally {
      zipkin.shutdown();
    }
  }
}
