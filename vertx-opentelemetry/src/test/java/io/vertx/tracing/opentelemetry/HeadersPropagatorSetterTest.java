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

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class HeadersPropagatorSetterTest {

  @Test
  public void shouldCallConsumerWhenCarrierIsNotNull() {

    final AtomicInteger nCalls = new AtomicInteger(0);
    final BiConsumer<String, String> c = (k, v) -> {
      nCalls.incrementAndGet();

      assertThat(k).isEqualTo("k");
      assertThat(v).isEqualTo("v");
    };

    final HeadersPropagatorSetter setter = new HeadersPropagatorSetter();

    setter.set(c, "k", "v");

    assertThat(nCalls.get()).isEqualTo(1);
  }

  @Test
  public void shouldNotThrowWhenCarrierIsNull() {
    assertThatNoException().isThrownBy(() -> new HeadersPropagatorSetter().set(null, "k", "v"));
  }
}
