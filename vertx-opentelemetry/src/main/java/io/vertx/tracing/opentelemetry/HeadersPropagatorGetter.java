/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tracing.opentelemetry;

import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

final class HeadersPropagatorGetter implements TextMapGetter<Iterable<Entry<String, String>>> {

  @Override
  public Iterable<String> keys(final Iterable<Entry<String, String>> carrier) {
    Set<String> keys = new HashSet<>();
    for (Entry<String, String> entry : carrier) {
      keys.add(entry.getKey());
    }
    return keys;
  }

  @Override
  public String get(final Iterable<Entry<String, String>> carrier, final String key) {
    if (carrier == null) {
      return null;
    }
    for (Entry<String, String> entry : carrier) {
      if (entry.getKey().equalsIgnoreCase(key)) {
        return entry.getValue();
      }
    }
    return null;
  }
}
