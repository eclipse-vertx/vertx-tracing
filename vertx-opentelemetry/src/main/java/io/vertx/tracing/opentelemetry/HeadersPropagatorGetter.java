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
package io.vertx.tracing.opentelemetry;

import io.opentelemetry.context.propagation.TextMapPropagator;

import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

final class HeadersPropagatorGetter implements TextMapPropagator.Getter<Iterable<Entry<String, String>>> {

  @Override
  public Iterable<String> keys(final Iterable<Entry<String, String>> carrier) {
    return StreamSupport.stream(carrier.spliterator(), false)
      .map(Entry::getKey)
      .collect(Collectors.toSet());
  }

  @Override
  public String get(final Iterable<Entry<String, String>> carrier, final String key) {
    if (carrier == null) {
      return null;
    }
    return StreamSupport.stream(carrier.spliterator(), false)
      .filter(e -> e.getKey().equalsIgnoreCase(key))
      .findFirst()
      .map(Entry::getValue)
      .orElse(null);
  }
}
