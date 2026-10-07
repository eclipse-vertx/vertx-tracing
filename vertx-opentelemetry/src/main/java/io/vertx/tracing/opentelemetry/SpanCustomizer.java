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

import io.opentelemetry.api.trace.SpanBuilder;

/**
 * Customizes the spans created by the Vert.x OpenTelemetry tracer before they are started.
 * <p>
 * Attributes set on the {@link SpanBuilder} are visible to the {@code Sampler}, which makes head sampling
 * decisions based on data extracted from the traced request possible.
 * <p>
 * The customizer is invoked on the thread creating the span, often an event-loop thread: implementations
 * must not block.
 */
@FunctionalInterface
public interface SpanCustomizer {

  /**
   * Invoked before a span is started.
   *
   * @param spanBuilder the builder of the span about to be started
   * @param request the request object, e.g. {@link io.vertx.core.spi.observability.HttpRequest} for HTTP spans
   *                or {@link io.vertx.core.eventbus.Message} for EventBus spans
   */
  void customize(SpanBuilder spanBuilder, Object request);

}
