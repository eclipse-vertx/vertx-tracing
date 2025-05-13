package io.vertx.tests.opentracing.it.jaegercontainer;


import java.util.Collections;
import java.util.Set;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.jaegertracing.api_v2.QueryServiceGrpc;
import io.jaegertracing.internal.JaegerTracer;
import io.jaegertracing.internal.reporters.RemoteReporter;
import io.jaegertracing.internal.samplers.ConstSampler;
import io.jaegertracing.spi.Reporter;
import io.jaegertracing.spi.Sender;
import io.jaegertracing.testcontainers.JaegerAllInOne;
import io.jaegertracing.thrift.internal.senders.HttpSender;
import org.apache.thrift.transport.TTransportException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;

public class JaegerContainerAllIn extends GenericContainer<JaegerAllInOne> {

  public static final int JAEGER_QUERY_PORT = 16686;

  public static final int JAEGER_COLLECTOR_THRIFT_PORT = 14268;

  public static final int JAEGER_COLLECTOR_GRPC_PORT = 14250;

  public static final int JAEGER_ADMIN_PORT = 14269;

  public static final int ZIPKIN_PORT = 9411;

  public JaegerContainerAllIn(String dockerImageName) {
    super(dockerImageName);
    init();
  }

  protected void init() {
    waitingFor(new BoundPortHttpWaitStrategy(JAEGER_ADMIN_PORT));
    withEnv("COLLECTOR_ZIPKIN_HTTP_PORT",
            String.valueOf(ZIPKIN_PORT));
    withExposedPorts(JAEGER_ADMIN_PORT,
                     JAEGER_COLLECTOR_THRIFT_PORT,
                     JAEGER_COLLECTOR_GRPC_PORT,
                     JAEGER_QUERY_PORT,
                     ZIPKIN_PORT);
  }

  public int getCollectorThriftPort() {
    return getMappedPort(JAEGER_COLLECTOR_THRIFT_PORT);
  }

  public int getQueryPort() {
    return getMappedPort(JAEGER_QUERY_PORT);
  }

  public JaegerTracer createTracer(String serviceName) throws TTransportException {
    String endpoint =
      String.format("http://localhost:%d/api/traces",
                    getCollectorThriftPort());
    Sender sender = new HttpSender.Builder(endpoint)
      .build();
    Reporter reporter = new RemoteReporter.Builder()
      .withSender(sender)
      .build();
    JaegerTracer.Builder tracerBuilder = new JaegerTracer.Builder(serviceName)
      .withSampler(new ConstSampler(true))
      .withReporter(reporter);
    return tracerBuilder.build();
  }

  public QueryServiceGrpc.QueryServiceBlockingStub createBlockingQueryService() {
    ManagedChannel channel =
      ManagedChannelBuilder.forTarget(
        String.format("localhost:%d",
                      getQueryPort())).usePlaintext().build();
    return QueryServiceGrpc.newBlockingStub(channel);
  }

  public static class BoundPortHttpWaitStrategy extends HttpWaitStrategy {
    private final int port;

    public BoundPortHttpWaitStrategy(int port) {
      this.port = port;
    }

    @Override
    protected Set<Integer> getLivenessCheckPorts() {
      int mapptedPort = this.waitStrategyTarget.getMappedPort(port);
      return Collections.singleton(mapptedPort);
    }
  }
}
