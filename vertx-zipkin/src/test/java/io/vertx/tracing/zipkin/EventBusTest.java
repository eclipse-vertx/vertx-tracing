/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tracing.zipkin;

import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;
import zipkin2.Span;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

public class EventBusTest extends ZipkinBaseTest {

  private static final String ADDRESS = "the-address";

  @Override
  protected HttpClientOptions getHttpClientOptions() {
    return new HttpClientOptions().setDefaultPort(8080);
  }

  @Test
  public void testEventBusSendPropagate(TestContext ctx) throws Exception {
    testSend(ctx, TracingPolicy.PROPAGATE, 2);
  }

  @Test
  public void testEventBusSendIgnore(TestContext ctx) throws Exception {
    testSend(ctx, TracingPolicy.IGNORE, 0);
  }

  @Test
  public void testEventBusSendAlways(TestContext ctx) throws Exception {
    testSend(ctx, TracingPolicy.ALWAYS, 2);
  }

  private void testSend(TestContext ctx, TracingPolicy policy, int expected) throws Exception {
    Async latch = ctx.async();
    ProducerVerticle producerVerticle = new ProducerVerticle(getHttpServerPolicy(policy), vertx -> {
      vertx.eventBus().send(ADDRESS, "ping", new DeliveryOptions().setTracingPolicy(policy));
      return Future.succeededFuture();
    });
    vertx.deployVerticle(producerVerticle).onComplete(ctx.asyncAssertSuccess(d1 -> {
      Promise<Void> consumerPromise = Promise.promise();
      vertx.deployVerticle(new ConsumerVerticle(consumerPromise)).onComplete(ctx.asyncAssertSuccess(d2 -> {
        client.request(HttpMethod.GET, "/").onComplete(ctx.asyncAssertSuccess(req -> {
          req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
            ctx.assertEquals(200, resp.statusCode());
            consumerPromise.future().onComplete(ctx.asyncAssertSuccess(v -> latch.complete()));
          }));
        }));
      }));
    }));
    latch.awaitSuccess();
    List<Span> spans = waitUntilTrace(() -> {
      return Optional.of(zipkin.getTraces())
        .filter(traces -> !traces.isEmpty())
        .map(traces -> traces.get(0))
        .map(trace -> trace.stream().filter(span -> !span.tags().containsKey("http.path")).collect(toList()))
        .filter(trace -> trace.size() == expected);
    });
    for (Span span : spans) {
      assertEquals("send", span.name());
      assertEquals(ADDRESS, span.remoteEndpoint().serviceName());
    }
  }

  private TracingPolicy getHttpServerPolicy(TracingPolicy policy) {
    return policy == TracingPolicy.ALWAYS ? TracingPolicy.IGNORE : TracingPolicy.ALWAYS;
  }

  @Test
  public void testEventBusPublishProgagate(TestContext ctx) throws Exception {
    testPublish(ctx, TracingPolicy.PROPAGATE, 3);
  }

  @Test
  public void testEventBusPublishIgnore(TestContext ctx) throws Exception {
    testPublish(ctx, TracingPolicy.IGNORE, 0);
  }

  @Test
  public void testEventBusPublishAlways(TestContext ctx) throws Exception {
    testPublish(ctx, TracingPolicy.ALWAYS, 3);
  }

  private void testPublish(TestContext ctx, TracingPolicy policy, int expected) throws Exception {
    Async latch = ctx.async();
    ProducerVerticle producerVerticle = new ProducerVerticle(getHttpServerPolicy(policy), vertx -> {
      vertx.eventBus().publish(ADDRESS, "ping", new DeliveryOptions().setTracingPolicy(policy));
      return Future.succeededFuture();
    });
    vertx.deployVerticle(producerVerticle).onComplete(ctx.asyncAssertSuccess(d1 -> {
      Promise<Void> consumer1Promise = Promise.promise();
      Promise<Void> consumer2Promise = Promise.promise();
      vertx.deployVerticle(new ConsumerVerticle(consumer1Promise)).onComplete(ctx.asyncAssertSuccess(d2 -> {
        vertx.deployVerticle(new ConsumerVerticle(consumer2Promise)).onComplete(ctx.asyncAssertSuccess(d3 -> {
          client.request(HttpMethod.GET, "/").onComplete(ctx.asyncAssertSuccess(req -> {
            req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
              ctx.assertEquals(200, resp.statusCode());
              Future.all(consumer1Promise.future(), consumer2Promise.future()).onComplete(ctx.asyncAssertSuccess(v -> latch.complete()));
            }));
          }));
        }));
      }));
    }));
    latch.awaitSuccess();
    List<Span> spans = waitUntilTrace(() -> {
      return Optional.of(zipkin.getTraces())
        .filter(traces -> !traces.isEmpty())
        .map(traces -> traces.get(0))
        .map(trace -> trace.stream().filter(span -> !span.tags().containsKey("http.path")).collect(toList()))
        .filter(trace -> trace.size() == expected);
    });
    for (Span span : spans) {
      assertEquals("publish", span.name());
      assertEquals(ADDRESS, span.remoteEndpoint().serviceName());
    }
  }

  @Test
  public void testEventBusRequestReplyPropagate(TestContext ctx) throws Exception {
    testRequestReply(ctx, TracingPolicy.PROPAGATE, false, 2);
  }

  @Test
  public void testEventBusRequestReplyIgnore(TestContext ctx) throws Exception {
    testRequestReply(ctx, TracingPolicy.IGNORE, false, 0);
  }

  @Test
  public void testEventBusRequestReplyAlways(TestContext ctx) throws Exception {
    testRequestReply(ctx, TracingPolicy.ALWAYS, false, 2);
  }

  @Test
  public void testEventBusRequestReplyFailurePropagate(TestContext ctx) throws Exception {
    testRequestReply(ctx, TracingPolicy.PROPAGATE, true, 2);
  }

  @Test
  public void testEventBusRequestReplyFailureIgnore(TestContext ctx) throws Exception {
    testRequestReply(ctx, TracingPolicy.IGNORE, true, 0);
  }

  @Test
  public void testEventBusRequestReplyFailureAlways(TestContext ctx) throws Exception {
    testRequestReply(ctx, TracingPolicy.ALWAYS, true, 2);
  }

  private void testRequestReply(TestContext ctx, TracingPolicy policy, boolean fail, int expected) throws Exception {
    Async latch = ctx.async();
    ProducerVerticle producerVerticle = new ProducerVerticle(getHttpServerPolicy(policy), vertx -> {
      Promise<Void> promise = Promise.promise();
      vertx.eventBus().request(ADDRESS, "ping", new DeliveryOptions().setTracingPolicy(policy)).onComplete(ar -> {
        if (ar.failed() == fail) {
          vertx.runOnContext(v -> promise.complete());
        } else {
          vertx.runOnContext(v -> promise.fail("Unexpected"));
        }
      });
      return promise.future();
    });
    vertx.deployVerticle(producerVerticle).onComplete(ctx.asyncAssertSuccess(d1 -> {
      vertx.deployVerticle(new ReplyVerticle(fail)).onComplete(ctx.asyncAssertSuccess(d2 -> {
        client.request(HttpMethod.GET, "/").onComplete(ctx.asyncAssertSuccess(req -> {
          req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
            ctx.assertEquals(200, resp.statusCode());
            latch.complete();
          }));
        }));
      }));
    }));
    latch.awaitSuccess();
    List<Span> spans = waitUntilTrace(() -> {
      return Optional.of(zipkin.getTraces())
        .filter(traces -> !traces.isEmpty())
        .map(traces -> traces.get(0))
        .map(trace -> trace.stream().filter(span -> !span.tags().containsKey("http.path")).collect(toList()))
        .filter(trace -> trace.size() == expected);
    });
    for (Span span : spans) {
      assertEquals("send", span.name());
      assertEquals(ADDRESS, span.remoteEndpoint().serviceName());
    }
  }

  private static class ProducerVerticle extends AbstractVerticle {

    private final TracingPolicy httpServerPolicy;
    private final Function<Vertx, Future<Void>> action;

    private ProducerVerticle(TracingPolicy httpServerPolicy, Function<Vertx, Future<Void>> action) {
      this.httpServerPolicy = httpServerPolicy;
      this.action = action;
    }

    @Override
    public void start(Promise<Void> startPromise) {
      vertx.createHttpServer(new HttpServerOptions().setTracingPolicy(httpServerPolicy))
        .requestHandler(this::onRequest)
        .listen(8080)
        .<Void>mapEmpty()
        .onComplete(startPromise);
    }

    private void onRequest(HttpServerRequest request) {
      action.apply(vertx).onComplete(ar -> {
        if (ar.succeeded()) {
          request.response().end();
        } else {
          ar.cause().printStackTrace();
          request.response().setStatusCode(500).end();
        }
      });
    }
  }

  private static class ConsumerVerticle extends AbstractVerticle {

    final Promise<Void> promise;

    ConsumerVerticle(Promise<Void> promise) {
      this.promise = promise;
    }

    @Override
    public void start(Promise<Void> startPromise) {
      vertx.eventBus().consumer(ADDRESS, msg -> {
        vertx.runOnContext(v -> promise.complete());
      }).completion().onComplete(startPromise);
    }
  }

  private static class ReplyVerticle extends AbstractVerticle {

    final boolean fail;

    ReplyVerticle(boolean fail) {
      this.fail = fail;
    }

    @Override
    public void start(Promise<Void> startPromise) {
      vertx.eventBus().consumer(ADDRESS, msg -> {
        if (fail) {
          msg.fail(10, "boom");
        } else {
          msg.reply(msg.body());
        }
      }).completion().onComplete(startPromise);
    }
  }
}
