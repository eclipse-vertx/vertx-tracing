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

import brave.Tracing;
import brave.http.HttpTracing;
import brave.sampler.Sampler;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingOptions;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;

import java.util.Objects;

@DataObject
@JsonGen(publicConverter = false)
public class ZipkinTracingOptions extends TracingOptions {

  public static final String DEFAULT_SERVICE_NAME = "a-service";
  public static final boolean DEFAULT_SUPPORTS_JOIN = true;

  private String serviceName = DEFAULT_SERVICE_NAME;
  private Sampler sampler = Sampler.ALWAYS_SAMPLE;
  private boolean supportsJoin = DEFAULT_SUPPORTS_JOIN;
  private HttpSenderOptions senderOptions = new HttpSenderOptions();
  private HttpTracing httpTracing;

  public ZipkinTracingOptions(HttpTracing httpTracing) {
    this.httpTracing = httpTracing;
  }

  public ZipkinTracingOptions(Tracing tracing) {
    this.httpTracing = HttpTracing.newBuilder(tracing).build();
  }

  public ZipkinTracingOptions() {
  }

  public ZipkinTracingOptions(ZipkinTracingOptions other) {
    this.serviceName = other.serviceName;
    this.sampler = other.sampler;
    this.supportsJoin = other.supportsJoin;
    this.senderOptions = other.senderOptions == null ? null : new HttpSenderOptions(other.senderOptions);
    this.httpTracing = other.httpTracing == null ? null : other.httpTracing.toBuilder().build();
  }

  public ZipkinTracingOptions(JsonObject json) {
    super(json);
    ZipkinTracingOptionsConverter.fromJson(json, this);
  }

  @Override
  public ZipkinTracingOptions copy() {
    return new ZipkinTracingOptions(this);
  }

  /**
   * @return the service name
   */
  public String getServiceName() {
    return serviceName;
  }

  /**
   * Set the service name to use.
   *
   * @param serviceName the service name
   * @return this instance
   */
  public ZipkinTracingOptions setServiceName(String serviceName) {
    Objects.requireNonNull(serviceName, "Service name cannot be null");
    this.serviceName = serviceName;
    return this;
  }

  /**
   * @return {@link brave.Tracing.Builder#supportsJoin(boolean)} option value
   */
  public boolean isSupportsJoin() {
    return supportsJoin;
  }

  /**
   * Configures {@link brave.Tracing.Builder#supportsJoin(boolean)} option.
   *
   * @param supportsJoin the config value
   * @return this instance
   */
  public ZipkinTracingOptions setSupportsJoin(boolean supportsJoin) {
    this.supportsJoin = supportsJoin;
    return this;
  }

  /**
   * @return the sender options
   */
  public HttpSenderOptions getSenderOptions() {
    return senderOptions;
  }

  /**
   * Set the HTTP sender options to use for reporting spans.
   *
   * @param senderOptions the options
   * @return this instance
   */
  public ZipkinTracingOptions setSenderOptions(HttpSenderOptions senderOptions) {
    this.senderOptions = senderOptions;
    return this;
  }

  /**
   * @return the Zipkin Sampler
   */
  public Sampler getSampler() {
    return sampler;
  }

  /**
   * Set the Zipkin Sampler.
   *
   * @param sampler the options
   * @return this instance
   */
  public ZipkinTracingOptions setSampler(Sampler sampler) {
    this.sampler = sampler;
    return this;
  }

  /**
   * Build the tracer and return it.
   */
  public ZipkinTracer buildTracer() {
    if (httpTracing != null) {
      return new ZipkinTracer(false, httpTracing, null);
    } else if (senderOptions != null) {
      String localServiceName = serviceName;
      VertxSender sender = new VertxSender(senderOptions);
      Tracing tracing = Tracing
        .newBuilder()
        .supportsJoin(supportsJoin)
        .localServiceName(localServiceName)
        .addSpanHandler(AsyncZipkinSpanHandler.create(sender))
        .sampler(sampler)
        .build();
      return new ZipkinTracer(true, tracing, sender);
    } else {
      return null;
    }
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    ZipkinTracingOptionsConverter.toJson(this, json);
    return json;
  }
}
