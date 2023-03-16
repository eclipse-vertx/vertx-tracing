package io.vertx.tracing.opentracing.it;

import java.time.Duration;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.tracing.TracingPolicy;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tracing.opentracing.OpenTracingOptions;
import io.vertx.tracing.opentracing.OpenTracingUtil;
import io.vertx.tracing.opentracing.it.jaegercontainer.JaegerContainerAllIn;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasToString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class CustomTracingIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(CustomTracingIT.class);

  private static final JaegerContainerAllIn JAEGER_ALL_IN_ONE = new JaegerContainerAllIn("quay.io/jaegertracing/all-in-one:latest");

  // it has to match with environment variable <JAEGER_SERVICE_NAME>test-traced-service</JAEGER_SERVICE_NAME> in pom.xml
  private static final String JAEGER_SERVICE_NAME = "test-traced-service";

  private static Vertx tracedVertx;

  private static Tracer tracer;

  @BeforeAll
  public static void deploy(VertxTestContext context) {
    Checkpoint deployCheck = context.checkpoint(2);
    Checkpoint statusCheck = context.checkpoint();
    context.verify(() -> {
      JAEGER_ALL_IN_ONE.start();
      tracer = JAEGER_ALL_IN_ONE.createTracer(JAEGER_SERVICE_NAME);
      deployCheck.flag();
      tracedVertx = Vertx.vertx(
        new VertxOptions().setTracingOptions(
          new OpenTracingOptions(tracer))
      );
      HttpClient client = tracedVertx.createHttpClient();
      tracedVertx.deployVerticle(ServerVerticle.class.getName()).onComplete(context.succeeding(id -> {
        client.request(HttpMethod.GET, 8080,"localhost","/health")
          .compose(req -> req.send().compose(resp -> resp.end().map(resp.statusCode()))).
          onComplete(context.succeeding(sc -> {
            context.verify(() ->{
              assertThat(sc).isEqualTo(200);
              statusCheck.flag();
            });
          }));
        deployCheck.flag();
      }));
    });
  }

  @AfterAll
  public static void cleanUp() {
    tracer.close();
    JAEGER_ALL_IN_ONE.stop();
    tracedVertx.close().onComplete(result -> {
      if (result.succeeded()) LOGGER.debug("Closing traced vertx OK.");
      else LOGGER.error("Closing traced vertx FAILED: " + result.cause());
    });
  }

  @BeforeEach
  public void httpCall(VertxTestContext context) {
    final Checkpoint checkpoint = context.checkpoint();
    HttpClient client = tracedVertx.createHttpClient(
      new HttpClientOptions()
        .setTracingPolicy(TracingPolicy.ALWAYS)
        .setDefaultPort(8080)
        .setDefaultHost("localhost")
    );
    // custom span
    Span span = tracer.buildSpan("custom-span")
      .withTag("custom-key", "custom-value")
      .start();
    OpenTracingUtil.setSpan(span);
    // client GET
    client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(httpClientRequest -> httpClientRequest.send().compose(HttpClientResponse::body)
        .onSuccess(body -> {
          assertEquals("Hello from the Server", body.toString());
          client.close();
        }).onFailure(throwable -> LOGGER.error("Error: {}", throwable.getCause().toString())));
    span.finish();
    // send eventbus-message
    DeliveryOptions options = new DeliveryOptions().setTracingPolicy(TracingPolicy.ALWAYS);
    tracedVertx.eventBus().send("eventbus-address", "eventbus-message", options);
    checkpoint.flag();
  }

  @Test
  @DisplayName("traceJson")
  public void traceJson(VertxTestContext context) {
    Checkpoint checkTrace = context.checkpoint();
    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      Response response = given()
        .get("http://localhost:" + JAEGER_ALL_IN_ONE.getQueryPort() + "/api/traces?service=" + JAEGER_SERVICE_NAME);
      response
        .then().statusCode(200)
        .defaultParser(Parser.JSON)
        .body("data", hasSize(4))
        .body("data.spans.tags", hasToString(containsString(
          "{key=http.status_code, type=string, value=200}")))
        // trace contains call to /health
        .body("data.spans.tags", hasToString(containsString(
          "{key=http.url, type=string, value=http://localhost:8080/health}")))
        // call of client
        .body("data.spans.tags", hasToString(containsString(
          "{key=span.kind, type=string, value=client}")))
        // call of server
        .body("data.spans.tags", hasToString(containsString(
          "{key=span.kind, type=string, value=server}")))

        // contains custom span
        .body("data.spans.operationName", hasToString(containsString("custom-span")))
        // with custom K,V
        .body("data.spans.tags", hasToString(containsString(
          "{key=custom-key, type=string, value=custom-value}")))

        // call to eventbus with address
        .body("data.spans.tags", hasToString(containsString(
          "{key=message_bus.destination, type=string, value=eventbus-address}")))
        // log with error
        .body("data.spans.logs", hasToString(containsString(
          "{key=error.kind, type=string, value=Exception}")))
        // with exception message
        .body("data.spans.logs", hasToString(containsString(
          "{key=message, type=string, value=No handlers for address eventbus-address}")));
      checkTrace.flag();
    });
  }
}
