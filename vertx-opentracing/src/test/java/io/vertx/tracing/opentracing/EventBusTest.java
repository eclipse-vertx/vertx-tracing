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

package io.vertx.tracing.opentracing;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.*;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Function;

@RunWith(VertxUnitRunner.class)
public class EventBusTest {

  private static final String ADDRESS = "the-address";

  private MockTracer tracer;
  private Vertx vertx;
  private HttpClient client;

  @Before
  public void before() {
    tracer = new MockTracer();
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new OpenTracingOptions(tracer)));
    client = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(8080));
  }

  @After
  public void after(TestContext context) {
    client.close();
    vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testEventBusSendPropagate(TestContext ctx) {
    testSend(ctx, TracingPolicy.PROPAGATE, 2);
  }

  @Test
  public void testEventBusSendIgnore(TestContext ctx) {
    testSend(ctx, TracingPolicy.IGNORE, 0);
  }

  @Test
  public void testEventBusSendAlways(TestContext ctx) {
    testSend(ctx, TracingPolicy.ALWAYS, 2);
  }

  private void testSend(TestContext ctx, TracingPolicy policy, int expected) {
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
            consumerPromise.future().onComplete(ctx.asyncAssertSuccess(v -> {
              int count = 0;
              for (MockSpan span : tracer.finishedSpans()) {
                String operationName = span.operationName();
                if (!operationName.equals("GET")) {
                  count++;
                  ctx.assertEquals("send", operationName);
                  ctx.assertEquals(ADDRESS, span.tags().get("message_bus.destination"));
                }
              }
              ctx.assertEquals(expected, count);
            }));
          }));
        }));
      }));
    }));
  }

  private TracingPolicy getHttpServerPolicy(TracingPolicy policy) {
    return policy == TracingPolicy.ALWAYS ? TracingPolicy.IGNORE : TracingPolicy.ALWAYS;
  }

  @Test
  public void testEventBusPublishProgagate(TestContext ctx) {
    testPublish(ctx, TracingPolicy.PROPAGATE, 3);
  }

  @Test
  public void testEventBusPublishIgnore(TestContext ctx) {
    testPublish(ctx, TracingPolicy.IGNORE, 0);
  }

  @Test
  public void testEventBusPublishAlways(TestContext ctx) {
    testPublish(ctx, TracingPolicy.ALWAYS, 3);
  }

  private void testPublish(TestContext ctx, TracingPolicy policy, int expected) {
    ProducerVerticle producerVerticle = new ProducerVerticle(getHttpServerPolicy(policy), vertx -> {
      vertx.eventBus().publish(ADDRESS, "ping", new DeliveryOptions().setTracingPolicy(policy));
      return Future.succeededFuture();
    });
    vertx.deployVerticle(producerVerticle).onComplete(ctx.asyncAssertSuccess(d1 -> {
      Promise<Void> consumer1Promise = Promise.promise();
      Promise<Void> consumer2Promise = Promise.promise();
      vertx.deployVerticle(new ConsumerVerticle(consumer1Promise)).onComplete(ctx.asyncAssertSuccess(d2 -> {
        vertx.deployVerticle(new ConsumerVerticle(consumer2Promise)).onComplete( ctx.asyncAssertSuccess(d3 -> {
          client.request(HttpMethod.GET, "/").onComplete(ctx.asyncAssertSuccess(req -> {
            req.send().onComplete(ctx.asyncAssertSuccess(resp -> {
              ctx.assertEquals(200, resp.statusCode());
              CompositeFuture.all(consumer1Promise.future(), consumer2Promise.future()).onComplete(ctx.asyncAssertSuccess(v -> {
                int count = 0;
                for (MockSpan span : tracer.finishedSpans()) {
                  String operationName = span.operationName();
                  if (!operationName.equals("GET")) {
                    count++;
                    ctx.assertEquals("publish", operationName);
                    ctx.assertEquals(ADDRESS, span.tags().get("message_bus.destination"));
                  }
                }
                ctx.assertEquals(expected, count);
              }));
            }));
          }));
        }));
      }));
    }));
  }

  @Test
  public void testEventBusRequestReplyPropagate(TestContext ctx) {
    testRequestReply(ctx, TracingPolicy.PROPAGATE, false, 2);
  }

  @Test
  public void testEventBusRequestReplyIgnore(TestContext ctx) {
    testRequestReply(ctx, TracingPolicy.IGNORE, false, 0);
  }

  @Test
  public void testEventBusRequestReplyAlways(TestContext ctx) {
    testRequestReply(ctx, TracingPolicy.ALWAYS, false, 2);
  }

  @Test
  public void testEventBusRequestReplyFailurePropagate(TestContext ctx) {
    testRequestReply(ctx, TracingPolicy.PROPAGATE, true, 2);
  }

  @Test
  public void testEventBusRequestReplyFailureIgnore(TestContext ctx) {
    testRequestReply(ctx, TracingPolicy.IGNORE, true, 0);
  }

  @Test
  public void testEventBusRequestReplyFailureAlways(TestContext ctx) {
    testRequestReply(ctx, TracingPolicy.ALWAYS, true, 2);
  }

  private void testRequestReply(TestContext ctx, TracingPolicy policy, boolean fail, int expected) {
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
            int count = 0;
            for (MockSpan span : tracer.finishedSpans()) {
              String operationName = span.operationName();
              if (!operationName.equals("GET")) {
                count++;
                ctx.assertEquals("send", operationName);
                ctx.assertEquals(ADDRESS, span.tags().get("message_bus.destination"));
              }
            }
            ctx.assertEquals(expected, count);
          }));
        }));
      }));
    }));
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
