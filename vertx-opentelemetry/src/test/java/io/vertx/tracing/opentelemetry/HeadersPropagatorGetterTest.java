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

import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class HeadersPropagatorGetterTest {

  @Test
  public void shouldGetAllKeys() {

    final HeadersPropagatorGetter getter = new HeadersPropagatorGetter();

    final Iterable<String> keys = getter.keys(Arrays.asList(
      new SimpleImmutableEntry<>("a", "1"),
      new SimpleImmutableEntry<>("b", "1")
    ));

    assertThat(keys).containsAll(Arrays.asList("a", "b"));
  }

  @Test
  public void shouldReturnNullWhenThereIsNotKeyInCarrier() {

    final HeadersPropagatorGetter getter = new HeadersPropagatorGetter();

    final Iterable<Map.Entry<String, String>> carrier = Arrays.asList(
      new SimpleImmutableEntry<>("a", "1"),
      new SimpleImmutableEntry<>("b", "1")
    );

    final String get = getter.get(carrier, "c");

    assertThat(get).isNull();
  }

  @Test
  public void shouldReturnValueWhenThereIsAKeyInCarrierCaseInsensitive() {

    final HeadersPropagatorGetter getter = new HeadersPropagatorGetter();

    final Iterable<Map.Entry<String, String>> carrier = Arrays.asList(
      new SimpleImmutableEntry<>("a", "1"),
      new SimpleImmutableEntry<>("b", "2")
    );

    final String get = getter.get(carrier, "A");

    assertThat(get).isEqualTo("1");
  }

  @Test
  public void shouldReturnNullWhenCarrierIsNull() {
    final HeadersPropagatorGetter getter = new HeadersPropagatorGetter();

    assertThat(
      getter.get(null, "A")
    ).isNull();
  }
}
