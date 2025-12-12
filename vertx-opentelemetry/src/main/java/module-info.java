/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
module io.vertx.tracing.opentelemetry {

  requires static io.vertx.docgen;
  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;

  requires io.opentelemetry.api;
  requires io.opentelemetry.context;
  requires io.opentelemetry.sdk;
  requires io.opentelemetry.sdk.trace;
  requires io.vertx.core;
  exports io.vertx.tracing.opentelemetry;
  provides io.opentelemetry.context.ContextStorageProvider with io.vertx.tracing.opentelemetry.VertxContextStorageProvider;
  provides io.vertx.core.spi.VertxServiceProvider with io.vertx.tracing.opentelemetry.OpenTelemetryTracingFactory;

}
