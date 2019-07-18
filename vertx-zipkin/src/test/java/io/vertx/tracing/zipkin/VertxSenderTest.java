package io.vertx.tracing.zipkin;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import zipkin2.junit.ZipkinRule;

@RunWith(VertxUnitRunner.class)
public class VertxSenderTest {

  private Vertx vertx;

  @Test
  public void testDefaultSenderEndpoint(TestContext ctx) throws Exception {
    ZipkinRule zipkin = new ZipkinRule();
    zipkin.start(9411);
    try {
      vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new ZipkinTracingOptions().setEnabled(true)));
      ZipkinTest.testHttpServerRequest(zipkin, vertx, ctx);
    } finally {
      zipkin.shutdown();
    }
  }
}
