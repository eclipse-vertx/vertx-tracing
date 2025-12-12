package io.vertx.tracing.zipkin;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter and mapper for {@link io.vertx.tracing.zipkin.HttpSenderOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.tracing.zipkin.HttpSenderOptions} original class using Vert.x codegen.
 */
public class HttpSenderOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, HttpSenderOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "senderEndpoint":
          if (member.getValue() instanceof String) {
            obj.setSenderEndpoint((String)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(HttpSenderOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(HttpSenderOptions obj, java.util.Map<String, Object> json) {
    if (obj.getSenderEndpoint() != null) {
      json.put("senderEndpoint", obj.getSenderEndpoint());
    }
  }
}
