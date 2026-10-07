/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tracing.opentelemetry;

/**
 * Provides the name of the spans created by the Vert.x OpenTelemetry tracer.
 * <p>
 * By default, spans are named after the operation reported by Vert.x, e.g. the HTTP method for HTTP spans.
 * A provider can compute a more meaningful name from the traced request, keeping cardinality under control.
 * <p>
 * The provider is invoked on the thread creating the span, often an event-loop thread: implementations
 * must not block.
 */
@FunctionalInterface
public interface SpanNameProvider {

  /**
   * Invoked before a span is started.
   *
   * @param operation the operation name reported by Vert.x, e.g. the HTTP method for HTTP spans
   * @param request the request object, e.g. {@link io.vertx.core.spi.observability.HttpRequest} for HTTP spans
   *                or {@link io.vertx.core.eventbus.Message} for EventBus spans
   * @return the span name, or {@code null} to use the operation name
   */
  String spanName(String operation, Object request);

}
