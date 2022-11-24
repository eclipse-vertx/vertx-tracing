package io.vertx.tracing.opentracing.it;

import java.time.Duration;

import io.opentracing.Tracer;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tracing.opentracing.OpenTracingOptions;
import io.vertx.tracing.opentracing.it.jaegercontainer.JaegerContainerAllIn;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class BasicTracingIT {
  private static final Logger LOGGER = LoggerFactory.getLogger(BasicTracingIT.class);

  private static Vertx tracedVertx;

  private static Tracer tracer;

  private static final String JAEGER_SERVICE_NAME = "test-traced-service";

  private static final JaegerContainerAllIn JAEGER_ALL_IN_ONE = new JaegerContainerAllIn("quay.io/jaegertracing/all-in-one:latest");

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
      WebClient webClient = WebClient.create(tracedVertx);
      tracedVertx.deployVerticle(ServerVerticle.class.getName(), context.succeeding(id -> {
        webClient.get(8080,"localhost","/health")
          .send(context.succeeding(bufferHttpResponse -> {
          context.verify(() ->{
            assertThat(bufferHttpResponse.statusCode()).isEqualTo(200);
            statusCheck.flag();
          });
        }));
        deployCheck.flag();
      }));
    });
  }

  @Test
  @DisplayName("simpleTrace")
  public void simpleTrace(VertxTestContext context) {
    System.out.println("Container name " + JAEGER_SERVICE_NAME);
    Checkpoint checkTrace = context.checkpoint();
    await().atMost(Duration.ofSeconds(12)).untilAsserted(() -> {
      Response response = given()
        .get("http://localhost:" + JAEGER_ALL_IN_ONE.getQueryPort() + "/api/traces?service=" + JAEGER_SERVICE_NAME);
      response
        .then().statusCode(200)
        .defaultParser(Parser.JSON)
        .body("data", hasSize(1))
        .body("data[0].spans", hasSize(1))
        .body("data[0].spans.operationName", hasItems("GET"))
        .body("data[0].spans.find {" +
                " it.operationName == 'GET' }" +
                ".tags.collect { \"${it.key}=${it.value}\".toString() }",
              hasItems(
                "http.status_code=200",
                "component=vertx",
                "span.kind=server",
                "sampler.type=const", // pom.xml JAEGER_SAMPLER_TYPE>const
                "http.url=http://localhost:8080/health",
                "http.method=GET"));
      checkTrace.flag();
    });
  }

  @AfterAll
  public static void cleanUp() {
    JAEGER_ALL_IN_ONE.stop();
    tracedVertx.close(result -> {
      if (result.succeeded()) LOGGER.debug("Closing traced vertx OK.");
      else LOGGER.error("Closing traced vertx FAILED: " + result.cause());
    });
  }
}
