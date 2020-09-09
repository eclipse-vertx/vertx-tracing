/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tracing.zipkin;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JdkSSLEngineOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.OpenSSLEngineOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.TrustOptions;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Options for reporting to a Zipkin server configured by default to {@code http://localhost:9411/api/v2/spans}.
 */
@DataObject
public class HttpSenderOptions extends HttpClientOptions {

  public static final String DEFAULT_SENDER_ENDPOINT = "http://localhost:9411/api/v2/spans";

  private String senderEndpoint;

  public HttpSenderOptions() {
    super();
    init();
  }

  private void init() {
    senderEndpoint = DEFAULT_SENDER_ENDPOINT;
    setMaxPoolSize(1);
    setTryUseCompression(true);
  }

  public HttpSenderOptions(JsonObject json) {
    super(json);
  }

  /**
   * @return
   */
  public String getSenderEndpoint() {
    return senderEndpoint;
  }

  public HttpSenderOptions setSenderEndpoint(String endpoint) {
    if (!(endpoint.startsWith("http://") || endpoint.startsWith("https://"))) {
      throw new IllegalArgumentException("Sender endpoint must be an absolute URL");
    }
    this.senderEndpoint = endpoint;
    return this;
  }

  @Override
  public HttpSenderOptions setSendBufferSize(int sendBufferSize) {
    return (HttpSenderOptions)super.setSendBufferSize(sendBufferSize);
  }

  @Override
  public HttpSenderOptions setReceiveBufferSize(int receiveBufferSize) {
    return (HttpSenderOptions)super.setReceiveBufferSize(receiveBufferSize);
  }

  @Override
  public HttpSenderOptions setReuseAddress(boolean reuseAddress) {
    return (HttpSenderOptions)super.setReuseAddress(reuseAddress);
  }

  @Override
  public HttpSenderOptions setReusePort(boolean reusePort) {
    return (HttpSenderOptions)super.setReusePort(reusePort);
  }

  @Override
  public HttpSenderOptions setTrafficClass(int trafficClass) {
    return (HttpSenderOptions)super.setTrafficClass(trafficClass);
  }

  @Override
  public HttpSenderOptions setTcpNoDelay(boolean tcpNoDelay) {
    return (HttpSenderOptions)super.setTcpNoDelay(tcpNoDelay);
  }

  @Override
  public HttpSenderOptions setTcpKeepAlive(boolean tcpKeepAlive) {
    return (HttpSenderOptions)super.setTcpKeepAlive(tcpKeepAlive);
  }

  @Override
  public HttpSenderOptions setSoLinger(int soLinger) {
    return (HttpSenderOptions)super.setSoLinger(soLinger);
  }

  @Override
  public HttpSenderOptions setIdleTimeout(int idleTimeout) {
    return (HttpSenderOptions)super.setIdleTimeout(idleTimeout);
  }

  @Override
  public HttpSenderOptions setIdleTimeoutUnit(TimeUnit idleTimeoutUnit) {
    return (HttpSenderOptions)super.setIdleTimeoutUnit(idleTimeoutUnit);
  }

  @Override
  public HttpSenderOptions setSsl(boolean ssl) {
    return (HttpSenderOptions)super.setSsl(ssl);
  }

  @Override
  public HttpSenderOptions setKeyCertOptions(KeyCertOptions options) {
    return (HttpSenderOptions)super.setKeyCertOptions(options);
  }

  @Override
  public HttpSenderOptions setKeyStoreOptions(JksOptions options) {
    return (HttpSenderOptions)super.setKeyStoreOptions(options);
  }

  @Override
  public HttpSenderOptions setPfxKeyCertOptions(PfxOptions options) {
    return (HttpSenderOptions)super.setPfxKeyCertOptions(options);
  }

  @Override
  public HttpSenderOptions setTrustOptions(TrustOptions options) {
    return (HttpSenderOptions)super.setTrustOptions(options);
  }

  @Override
  public HttpSenderOptions setPemKeyCertOptions(PemKeyCertOptions options) {
    return (HttpSenderOptions)super.setPemKeyCertOptions(options);
  }

  @Override
  public HttpSenderOptions setTrustStoreOptions(JksOptions options) {
    return (HttpSenderOptions)super.setTrustStoreOptions(options);
  }

  @Override
  public HttpSenderOptions setPfxTrustOptions(PfxOptions options) {
    return (HttpSenderOptions)super.setPfxTrustOptions(options);
  }

  @Override
  public HttpSenderOptions setPemTrustOptions(PemTrustOptions options) {
    return (HttpSenderOptions)super.setPemTrustOptions(options);
  }

  @Override
  public HttpSenderOptions addEnabledCipherSuite(String suite) {
    return (HttpSenderOptions)super.addEnabledCipherSuite(suite);
  }

  @Override
  public HttpSenderOptions addEnabledSecureTransportProtocol(String protocol) {
    return (HttpSenderOptions)super.addEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public HttpSenderOptions removeEnabledSecureTransportProtocol(String protocol) {
    return (HttpSenderOptions)super.removeEnabledSecureTransportProtocol(protocol);
  }

  @Override
  public HttpSenderOptions setTcpFastOpen(boolean tcpFastOpen) {
    return (HttpSenderOptions)super.setTcpFastOpen(tcpFastOpen);
  }

  @Override
  public HttpSenderOptions setTcpCork(boolean tcpCork) {
    return (HttpSenderOptions)super.setTcpCork(tcpCork);
  }

  @Override
  public HttpSenderOptions setTcpQuickAck(boolean tcpQuickAck) {
    return (HttpSenderOptions)super.setTcpQuickAck(tcpQuickAck);
  }

  @Override
  public HttpSenderOptions addCrlPath(String crlPath) throws NullPointerException {
    return (HttpSenderOptions)super.addCrlPath(crlPath);
  }

  @Override
  public HttpSenderOptions addCrlValue(Buffer crlValue) throws NullPointerException {
    return (HttpSenderOptions)super.addCrlValue(crlValue);
  }

  @Override
  public HttpSenderOptions setConnectTimeout(int connectTimeout) {
    return (HttpSenderOptions)super.setConnectTimeout(connectTimeout);
  }

  @Override
  public HttpSenderOptions setTrustAll(boolean trustAll) {
    return (HttpSenderOptions)super.setTrustAll(trustAll);
  }

  @Override
  public HttpSenderOptions setEnabledSecureTransportProtocols(Set<String> enabledSecureTransportProtocols) {
    return (HttpSenderOptions)super.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
  }

  @Override
  public HttpSenderOptions setMaxPoolSize(int maxPoolSize) {
    return (HttpSenderOptions)super.setMaxPoolSize(maxPoolSize);
  }

  @Override
  public HttpSenderOptions setHttp2MultiplexingLimit(int limit) {
    return (HttpSenderOptions)super.setHttp2MultiplexingLimit(limit);
  }

  @Override
  public HttpSenderOptions setHttp2MaxPoolSize(int max) {
    return (HttpSenderOptions)super.setHttp2MaxPoolSize(max);
  }

  @Override
  public HttpSenderOptions setHttp2ConnectionWindowSize(int http2ConnectionWindowSize) {
    return (HttpSenderOptions)super.setHttp2ConnectionWindowSize(http2ConnectionWindowSize);
  }

  @Override
  public HttpSenderOptions setHttp2KeepAliveTimeout(int keepAliveTimeout) {
    return (HttpSenderOptions)super.setHttp2KeepAliveTimeout(keepAliveTimeout);
  }

  @Override
  public HttpSenderOptions setKeepAlive(boolean keepAlive) {
    return (HttpSenderOptions)super.setKeepAlive(keepAlive);
  }

  @Override
  public HttpSenderOptions setKeepAliveTimeout(int keepAliveTimeout) {
    return (HttpSenderOptions)super.setKeepAliveTimeout(keepAliveTimeout);
  }

  @Override
  public HttpSenderOptions setPipelining(boolean pipelining) {
    return (HttpSenderOptions)super.setPipelining(pipelining);
  }

  @Override
  public HttpSenderOptions setPipeliningLimit(int limit) {
    return (HttpSenderOptions)super.setPipeliningLimit(limit);
  }

  @Override
  public HttpSenderOptions setVerifyHost(boolean verifyHost) {
    return (HttpSenderOptions)super.setVerifyHost(verifyHost);
  }

  @Override
  public HttpSenderOptions setTryUseCompression(boolean tryUseCompression) {
    return (HttpSenderOptions)super.setTryUseCompression(tryUseCompression);
  }

  @Override
  public HttpSenderOptions setSendUnmaskedFrames(boolean sendUnmaskedFrames) {
    return (HttpSenderOptions)super.setSendUnmaskedFrames(sendUnmaskedFrames);
  }

  @Override
  public HttpSenderOptions setMaxWebSocketFrameSize(int maxWebsocketFrameSize) {
    return (HttpSenderOptions)super.setMaxWebSocketFrameSize(maxWebsocketFrameSize);
  }

  @Override
  public HttpSenderOptions setMaxWebSocketMessageSize(int maxWebsocketMessageSize) {
    return (HttpSenderOptions)super.setMaxWebSocketMessageSize(maxWebsocketMessageSize);
  }

  @Override
  public HttpSenderOptions setDefaultHost(String defaultHost) {
    return (HttpSenderOptions)super.setDefaultHost(defaultHost);
  }

  @Override
  public HttpSenderOptions setDefaultPort(int defaultPort) {
    return (HttpSenderOptions)super.setDefaultPort(defaultPort);
  }

  @Override
  public HttpSenderOptions setProtocolVersion(HttpVersion protocolVersion) {
    return (HttpSenderOptions)super.setProtocolVersion(protocolVersion);
  }

  @Override
  public HttpSenderOptions setMaxChunkSize(int maxChunkSize) {
    return (HttpSenderOptions)super.setMaxChunkSize(maxChunkSize);
  }

  @Override
  public HttpSenderOptions setMaxInitialLineLength(int maxInitialLineLength) {
    return (HttpSenderOptions)super.setMaxInitialLineLength(maxInitialLineLength);
  }

  @Override
  public HttpSenderOptions setMaxHeaderSize(int maxHeaderSize) {
    return (HttpSenderOptions)super.setMaxHeaderSize(maxHeaderSize);
  }

  @Override
  public HttpSenderOptions setMaxWaitQueueSize(int maxWaitQueueSize) {
    return (HttpSenderOptions)super.setMaxWaitQueueSize(maxWaitQueueSize);
  }

  @Override
  public HttpSenderOptions setInitialSettings(Http2Settings settings) {
    return (HttpSenderOptions)super.setInitialSettings(settings);
  }

  @Override
  public HttpSenderOptions setUseAlpn(boolean useAlpn) {
    return (HttpSenderOptions)super.setUseAlpn(useAlpn);
  }

  @Override
  public HttpSenderOptions setSslEngineOptions(SSLEngineOptions sslEngineOptions) {
    return (HttpSenderOptions)super.setSslEngineOptions(sslEngineOptions);
  }

  @Override
  public HttpSenderOptions setJdkSslEngineOptions(JdkSSLEngineOptions sslEngineOptions) {
    return (HttpSenderOptions)super.setJdkSslEngineOptions(sslEngineOptions);
  }

  @Override
  public HttpSenderOptions setOpenSslEngineOptions(OpenSSLEngineOptions sslEngineOptions) {
    return (HttpSenderOptions)super.setOpenSslEngineOptions(sslEngineOptions);
  }

  @Override
  public HttpSenderOptions setAlpnVersions(List<HttpVersion> alpnVersions) {
    return (HttpSenderOptions)super.setAlpnVersions(alpnVersions);
  }

  @Override
  public HttpSenderOptions setHttp2ClearTextUpgrade(boolean value) {
    return (HttpSenderOptions)super.setHttp2ClearTextUpgrade(value);
  }

  @Override
  public HttpSenderOptions setMaxRedirects(int maxRedirects) {
    return (HttpSenderOptions)super.setMaxRedirects(maxRedirects);
  }

  @Override
  public HttpSenderOptions setForceSni(boolean forceSni) {
    return (HttpSenderOptions)super.setForceSni(forceSni);
  }

  @Override
  public HttpSenderOptions setMetricsName(String metricsName) {
    return (HttpSenderOptions)super.setMetricsName(metricsName);
  }

  @Override
  public HttpSenderOptions setProxyOptions(ProxyOptions proxyOptions) {
    return (HttpSenderOptions)super.setProxyOptions(proxyOptions);
  }

  @Override
  public HttpSenderOptions setLocalAddress(String localAddress) {
    return (HttpSenderOptions)super.setLocalAddress(localAddress);
  }

  @Override
  public HttpSenderOptions setLogActivity(boolean logEnabled) {
    return (HttpSenderOptions)super.setLogActivity(logEnabled);
  }

  @Override
  public HttpSenderOptions setTryUsePerFrameWebSocketCompression(boolean offer) {
    return (HttpSenderOptions)super.setTryUsePerFrameWebSocketCompression(offer);
  }

  @Override
  public HttpSenderOptions setTryUsePerMessageWebSocketCompression(boolean offer) {
    return (HttpSenderOptions)super.setTryUsePerMessageWebSocketCompression(offer);
  }

  @Override
  public HttpSenderOptions setWebSocketCompressionLevel(int compressionLevel) {
    return (HttpSenderOptions)super.setWebSocketCompressionLevel(compressionLevel);
  }

  @Override
  public HttpSenderOptions setWebSocketCompressionAllowClientNoContext(boolean offer) {
    return (HttpSenderOptions)super.setWebSocketCompressionAllowClientNoContext(offer);
  }

  @Override
  public HttpSenderOptions setWebSocketCompressionRequestServerNoContext(boolean offer) {
    return (HttpSenderOptions)super.setWebSocketCompressionRequestServerNoContext(offer);
  }

  @Override
  public HttpSenderOptions setDecoderInitialBufferSize(int decoderInitialBufferSize) {
    return (HttpSenderOptions)super.setDecoderInitialBufferSize(decoderInitialBufferSize);
  }

  @Override
  public HttpSenderOptions setPoolCleanerPeriod(int poolCleanerPeriod) {
    return (HttpSenderOptions)super.setPoolCleanerPeriod(poolCleanerPeriod);
  }
}
