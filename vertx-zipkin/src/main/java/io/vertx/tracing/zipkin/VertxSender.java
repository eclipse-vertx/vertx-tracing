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

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.spi.VertxTracerFactory;
import zipkin2.reporter.BaseHttpSender;
import zipkin2.reporter.Encoding;
import zipkin2.reporter.HttpEndpointSuppliers;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An HTTP sender using Vert.x HttpClient, only JSON encoding is supported.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class VertxSender extends BaseHttpSender<RequestOptions, Buffer> {

  private static final CharSequence APPLICATION_JSON = HttpHeaders.createOptimized("application/json");

  private final int messageMaxBytes = 5242880;
  private final Vertx vertx;
  private final HttpClient client;
  private final HttpSenderOptions options;
  private final String endpoint;

  public VertxSender(HttpSenderOptions options) {
    super(Encoding.JSON, HttpEndpointSuppliers.constantFactory(), options.getSenderEndpoint());
    this.options = new HttpSenderOptions(options);
    this.endpoint = options.getSenderEndpoint();
    this.vertx = Vertx.builder().withTracer(VertxTracerFactory.NOOP).build();
    this.client = vertx.createHttpClient(options);
  }

  public HttpSenderOptions options() {
    return options;
  }

  @Override
  public Encoding encoding() {
    return Encoding.JSON;
  }

  @Override
  public int messageMaxBytes() {
    return messageMaxBytes;
  }

  @Override
  public int messageSizeInBytes(List<byte[]> encodedSpans) {
    int val = 2;
    int length = encodedSpans.size();
    for (int i = 0; i < length; i++) {
      if (i > 0) {
        ++val;
      }
      val += encodedSpans.get(i).length;
    }
    return val;
  }

  @Override
  protected RequestOptions newEndpoint(String endpoint) {
    RequestOptions options = new RequestOptions()
      .setMethod(HttpMethod.POST)
      .addHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
    if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
      options.setAbsoluteURI(endpoint);
    } else {
      options.setURI(endpoint);
    }
    return options;
  }

  @Override
  protected Buffer newBody(List<byte[]> encodedSpans) {
    int capacity = messageSizeInBytes(encodedSpans);
    Buffer body = Buffer.buffer(capacity);
    body.appendByte((byte) '[');
    for (int i = 0; i < encodedSpans.size(); i++) {
      if (i > 0) {
        body.appendByte((byte) ',');
      }
      body.appendBytes(encodedSpans.get(i));
    }
    body.appendByte((byte) ']');
    return body;
  }

  @Override
  protected void postSpans(RequestOptions requestOptions, Buffer body) throws IOException {
    try {
      client.request(requestOptions)
        .compose(req -> req
          .send(body)
          .compose(HttpClientResponse::body))
        .await(20, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void doClose() {
    client.close();
    vertx.close();
  }
}
