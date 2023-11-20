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
package io.vertx.tracing.opentracing;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.tracing.TracingOptions;

@DataObject
public class OpenTracingOptions extends TracingOptions {

  public OpenTracingOptions() {
  }

  public OpenTracingOptions(OpenTracingOptions other) {
    super(other);
  }

  public OpenTracingOptions(JsonObject json) {
    super(json);
  }

  @Override
  public OpenTracingOptions copy() {
    return new OpenTracingOptions(this);
  }
}
