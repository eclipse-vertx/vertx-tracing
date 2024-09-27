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

package io.vertx.tracing.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.*;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class EventBusTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  private static final String ADDRESS = "the-address";

  private Vertx vertx;
  private HttpClient client;

  @BeforeEach
  public void setUp() throws Exception {
    vertx = Vertx.vertx(new VertxOptions().setTracingOptions(new OpenTelemetryOptions(otelTesting.getOpenTelemetry())));
    client = vertx.createHttpClient(new HttpClientOptions().setDefaultPort(8080));
  }

  @AfterEach
  public void tearDown(VertxTestContext context) throws Exception {
    vertx.close().onComplete(context.succeedingThenComplete());
  }

  @Test
  public void testEventBusSendPropagate(VertxTestContext ctx) {
    testSend(ctx, TracingPolicy.PROPAGATE, 2);
  }

  @Test
  public void testEventBusSendIgnore(VertxTestContext ctx) {
    testSend(ctx, TracingPolicy.IGNORE, 0);
  }

  @Test
  public void testEventBusSendAlways(VertxTestContext ctx) {
    testSend(ctx, TracingPolicy.ALWAYS, 2);
  }

  private void testSend(VertxTestContext ctx, TracingPolicy policy, int expected) {
    ProducerVerticle producerVerticle = new ProducerVerticle(getHttpServerPolicy(policy), vertx -> {
      vertx.eventBus().send(ADDRESS, "ping", new DeliveryOptions().setTracingPolicy(policy));
      return Future.succeededFuture();
    });
    vertx.deployVerticle(producerVerticle).onComplete(ctx.succeeding(d1 -> {
      Promise<Void> consumerPromise = Promise.promise();
      vertx.deployVerticle(new ConsumerVerticle(consumerPromise)).onComplete(ctx.succeeding(d2 ->
        client.request(HttpMethod.GET, "/")
          .compose(HttpClientRequest::send)
          .compose(HttpClientResponse::body)
          .onComplete(ctx.succeeding(body -> {}))
      ));
    }));
    long now = System.currentTimeMillis();
    while (true) {
      int count = 0;
      for (SpanData data : otelTesting.getSpans()) {
        String operationName = data.getName();
        assertThat(operationName).isNotNull();
        if (!operationName.equals("GET")) {
          count++;
          assertThat(operationName)
            .isEqualTo("send");
          assertThat(data.getAttributes().get(AttributeKey.stringKey("messaging.destination.name")))
            .isEqualTo(ADDRESS);
        }
      }
      if (count == expected) {
        break;
      }
      assertThat(System.currentTimeMillis() - now)
        .isLessThan(10_000L);
    }
    ctx.completeNow();
  }

  private TracingPolicy getHttpServerPolicy(TracingPolicy policy) {
    return policy == TracingPolicy.ALWAYS ? TracingPolicy.IGNORE : TracingPolicy.ALWAYS;
  }

  @Test
  public void testEventBusPublishPropagate(VertxTestContext ctx) {
    testPublish(ctx, TracingPolicy.PROPAGATE, 3);
  }

  @Test
  public void testEventBusPublishIgnore(VertxTestContext ctx) {
    testPublish(ctx, TracingPolicy.IGNORE, 0);
  }

  @Test
  public void testEventBusPublishAlways(VertxTestContext ctx) {
    testPublish(ctx, TracingPolicy.ALWAYS, 3);
  }

  private void testPublish(VertxTestContext ctx, TracingPolicy policy, int expected) {
    vertx.getOrCreateContext().runOnContext(c -> {
      Promise<Void> consumer1Promise = Promise.promise();
      Promise<Void> consumer2Promise = Promise.promise();

      ProducerVerticle producerVerticle = new ProducerVerticle(getHttpServerPolicy(policy), vertx -> {
        vertx.eventBus().publish(ADDRESS, "ping", new DeliveryOptions().setTracingPolicy(policy));
        return Future.succeededFuture();
      });
      ConsumerVerticle consumerVerticle1 = new ConsumerVerticle(consumer1Promise);
      ConsumerVerticle consumerVerticle2 = new ConsumerVerticle(consumer2Promise);

      ctx.assertComplete(Future.all(
        vertx.deployVerticle(producerVerticle),
        vertx.deployVerticle(consumerVerticle1),
        vertx.deployVerticle(consumerVerticle2)
      )).onSuccess(v ->
        client.request(HttpMethod.GET, "/").onComplete(ctx.succeeding(req ->
          req.send().onComplete(ctx.succeeding(resp -> {
            ctx.verify(() -> assertThat(resp.statusCode()).isEqualTo(200));
            ctx.assertComplete(Future.all(consumer1Promise.future(), consumer2Promise.future()))
              .onSuccess(v1 -> {
                ctx.verify(() -> {
                  int count = 0;
                  for (SpanData data : otelTesting.getSpans()) {
                    String operationName = data.getName();
                    assertThat(operationName).isNotNull();
                    if (!operationName.equals("GET")) {
                      count++;
                      assertThat(operationName)
                        .isEqualTo("publish");
                      assertThat(data.getAttributes().get(AttributeKey.stringKey("messaging.destination.name")))
                        .isEqualTo(ADDRESS);
                    }
                  }
                  assertThat(count).isEqualTo(expected);
                });
                ctx.completeNow();
              });
          }))
        ))
      );
    });
  }

  @Test
  public void testEventBusRequestReplyPropagate(VertxTestContext ctx) {
    testRequestReply(ctx, TracingPolicy.PROPAGATE, false, 2);
  }

  @Test
  public void testEventBusRequestReplyIgnore(VertxTestContext ctx) {
    testRequestReply(ctx, TracingPolicy.IGNORE, false, 0);
  }

  @Test
  public void testEventBusRequestReplyAlways(VertxTestContext ctx) {
    testRequestReply(ctx, TracingPolicy.ALWAYS, false, 2);
  }

  @Test
  public void testEventBusRequestReplyFailurePropagate(VertxTestContext ctx) {
    testRequestReply(ctx, TracingPolicy.PROPAGATE, true, 2);
  }

  @Test
  public void testEventBusRequestReplyFailureIgnore(VertxTestContext ctx) {
    testRequestReply(ctx, TracingPolicy.IGNORE, true, 0);
  }

  @Test
  public void testEventBusRequestReplyFailureAlways(VertxTestContext ctx) {
    testRequestReply(ctx, TracingPolicy.ALWAYS, true, 2);
  }

  private void testRequestReply(VertxTestContext ctx, TracingPolicy policy, boolean fail, int expected) {
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
    vertx.deployVerticle(producerVerticle).onComplete(ctx.succeeding(d1 -> {
      vertx.deployVerticle(new ReplyVerticle(fail)).onComplete(ctx.succeeding(d2 -> {
        client.request(HttpMethod.GET, "/").onComplete(ctx.succeeding(req -> {
          req.send().onComplete(ctx.succeeding(resp -> {
            ctx.verify(() -> {
              assertThat(resp.statusCode()).isEqualTo(200);
              int count = 0;
              for (SpanData data : otelTesting.getSpans()) {
                String operationName = data.getName();
                assertThat(operationName).isNotNull();
                if (!operationName.equals("GET")) {
                  count++;
                  assertThat(operationName)
                    .isEqualTo("send");
                  assertThat(data.getAttributes().get(AttributeKey.stringKey("messaging.destination.name")))
                    .isEqualTo(ADDRESS);
                }
              }
              assertThat(count).isEqualTo(expected);
            });
            ctx.completeNow();
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
